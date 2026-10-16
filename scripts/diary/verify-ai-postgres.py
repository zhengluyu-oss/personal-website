"""仅操作带本变更标签和固定镜像的专属 QA 容器；不读取生产配置。"""
import argparse, hashlib, json, os, pathlib, subprocess, time, uuid

ROOT=pathlib.Path(__file__).resolve().parent
IMAGE='pgvector/pgvector@sha256:131dcf7ff6a900545df8e7e092c270aa8c6db2f2c818e408cb45ec21316b74e6'
LABEL='add-diary-rag-and-ai-review'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--container');parser.add_argument('--keep',action='store_true');args=parser.parse_args()
    name=args.container or 'luyu-diary-ai-qa-'+str(os.getpid());created=False
    def command(*parts,body=None,expect=True):
        r=subprocess.run(list(parts),input=body,capture_output=True,timeout=90)
        if expect and r.returncode:raise AssertionError(r.stderr.decode(errors='replace')[-1500:])
        return r
    if not args.container:
        command('docker','run','-d','--name',name,'--label','codex.change='+LABEL,'--memory','512m','--cpus','1',
                '-e','POSTGRES_PASSWORD=isolated-diary-admin','-e','POSTGRES_DB=diary_qa',IMAGE);created=True
    info=json.loads(command('docker','inspect',name).stdout)[0]
    assert info['Config']['Labels'].get('codex.change')==LABEL
    assert info['Config']['Image']==IMAGE
    def sql(text,db='diary_qa',runtime=False,expect=True):
        parts=['docker','exec','-i']
        if runtime:parts+=['-e','PGPASSWORD=isolated-diary-runtime']
        parts+=[name,'psql','-h','127.0.0.1','-X','-qAt','-v','ON_ERROR_STOP=1','-U','diary_runtime' if runtime else 'postgres','-d',db]
        return command(*parts,body=('\\set VERBOSITY sqlstate\n'+text).encode(),expect=expect)
    def text(statement,**kw):return sql(statement,**kw).stdout.decode().strip()
    try:
        for _ in range(100):
            if sql('SELECT 1',expect=False).returncode==0:break
            time.sleep(.2)
        else:raise AssertionError('QA PostgreSQL not ready')
        assert text("SELECT default_version FROM pg_available_extensions WHERE name='vector'")=='0.8.3'
        sql((ROOT/'000-roles.sql').read_text(encoding='utf8'))
        sql("ALTER DATABASE diary_qa OWNER TO diary_migrator; ALTER ROLE diary_runtime LOGIN PASSWORD 'isolated-diary-runtime'; REVOKE ALL ON SCHEMA public FROM PUBLIC;")
        for script in ['001-private-diary.sql','000-vector.sql','002-private-diary-ai.sql','002-private-diary-ai.sql','001-private-diary.sql']:
            sql((ROOT/script).read_text(encoding='utf8'))
        assert text("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='diary_ai' AND c.relrowsecurity AND c.relforcerowsecurity")=='9'
        assert text("SELECT count(*) FROM pg_roles WHERE rolname='diary_runtime' AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolinherit")=='1'
        assert text("SELECT count(*) FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member WHERE r.rolname='diary_runtime'")=='0'
        owner=10_000_000_000+os.getpid();entry=str(uuid.uuid4());profile=hashlib.sha256(entry.encode()).hexdigest()
        setup=f"""BEGIN; SELECT set_config('diary.owner_id','{owner}',true);
INSERT INTO diary.entry(id,owner_id,title,body,revision) VALUES('{entry}',{owner},'synthetic','synthetic only',1);
INSERT INTO diary_ai.embedding_profile VALUES({owner},'{profile}','fixture','fixture',3,'fixture');
INSERT INTO diary_ai.entry_index(owner_id,entry_id,profile_id,source_revision,total_chunks,status) VALUES({owner},'{entry}','{profile}',1,1,'READY');
"""
        insert=f"INSERT INTO diary_ai.chunk VALUES({owner},'{entry}','{profile}',1,0,0,5,'{profile}',3,'[1,2,3]');"
        result=text(setup+insert+f"SELECT count(*) FROM diary_ai.chunk; SELECT set_config('diary.owner_id','{owner+1}',true); SELECT count(*) FROM diary_ai.chunk; ROLLBACK;",runtime=True).splitlines()
        assert result==[str(owner),'1',str(owner+1),'0']
        for statement,code in [(insert.replace('[1,2,3]','[1,2]'),'23514'),
                               (insert.replace(profile,'0'*64,1),'23503'),
                               (f"SELECT set_config('diary.owner_id','{owner+1}',true);"+insert,'42501')]:
            r=sql(setup+statement+'ROLLBACK;',runtime=True,expect=False)
            assert r.returncode and code in r.stderr.decode(),r.stderr.decode()
        assert text("SELECT count(*) FROM diary_ai.chunk; SELECT has_schema_privilege(current_user,'diary_ai','CREATE'); SELECT has_table_privilege(current_user,'diary.entry_revision','DELETE');",runtime=True).splitlines()==['0','f','f']
        sql('ALTER TABLE diary_ai.chunk ADD COLUMN qa_drift integer;')
        bad=sql((ROOT/'002-private-diary-ai.sql').read_text(encoding='utf8'),expect=False);assert bad.returncode
        sql('ALTER TABLE diary_ai.chunk DROP COLUMN qa_drift;')
        sql((ROOT/'002-private-diary-ai.sql').read_text(encoding='utf8'))
        failed='diary_ai_partial_'+str(os.getpid())
        sql('CREATE DATABASE '+failed+' OWNER diary_migrator;',db='postgres')
        sql((ROOT/'001-private-diary.sql').read_text(encoding='utf8'),db=failed)
        sql((ROOT/'000-vector.sql').read_text(encoding='utf8'),db=failed)
        injected=(ROOT/'002-private-diary-ai.sql').read_text(encoding='utf8').replace('CREATE TABLE diary_ai.chunk(','SELECT 1/0;\nCREATE TABLE diary_ai.chunk(')
        assert sql(injected,db=failed,expect=False).returncode
        assert text("SELECT to_regnamespace('diary_ai') IS NULL",db=failed)=='t'
        print(json.dumps({'migrationRepeat':True,'oldFingerprint':True,'forcedRlsTables':9,'dimensionConstraint':True,
                          'ownerCompositeFk':True,'noOwnerAndCrossOwnerDenied':True,'driftRefused':True,'failedMigrationAtomic':True,'pgvector':'0.8.3'}))
    finally:
        if created and not args.keep:
            actual=json.loads(command('docker','inspect',name).stdout)[0]
            assert actual['Id']==info['Id'] and actual['Config']['Labels'].get('codex.change')==LABEL
            command('docker','rm','-f',name)

if __name__=='__main__':main()
