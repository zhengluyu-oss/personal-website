"""Isolated synthetic-data verification. Never reads production settings."""
import json, os, pathlib, subprocess, time, uuid
ROOT = pathlib.Path(__file__).resolve().parents[2]
NAME = 'luyu-knowledge-qa-' + uuid.uuid4().hex[:10]
LABEL = 'add-knowledge-library-and-connections'
IMAGE = 'pgvector/pgvector@sha256:131dcf7ff6a900545df8e7e092c270aa8c6db2f2c818e408cb45ec21316b74e6'
def run(args, body=None, check=True):
    r = subprocess.run(args,input=body,capture_output=True,text=True,encoding='utf-8',errors='replace')
    if check and r.returncode: raise RuntimeError(r.stdout[-4000:]+r.stderr[-4000:])
    return r
def sql(body, check=True):
    return run(['docker','exec','-i',NAME,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','postgres','-d','knowledge_qa'],body,check)
receipt = None
try:
    run(['docker','run','-d','--name',NAME,'--label','codex.change='+LABEL,'--memory','512m','--cpus','1','-p','127.0.0.1:15433:5432','-e','POSTGRES_PASSWORD=isolated-knowledge-admin','-e','POSTGRES_DB=knowledge_qa',IMAGE])
    receipt=json.loads(run(['docker','inspect',NAME]).stdout)[0]
    for _ in range(100):
        if sql('SELECT 1',False).returncode==0: break
        time.sleep(.2)
    else: raise RuntimeError('QA database unavailable')
    sql((ROOT/'scripts/knowledge/000-roles.sql').read_text(encoding='utf-8'))
    sql("ALTER DATABASE knowledge_qa OWNER TO knowledge_migrator; ALTER ROLE knowledge_runtime LOGIN PASSWORD 'isolated-knowledge-runtime'; REVOKE ALL ON SCHEMA public FROM PUBLIC; CREATE SCHEMA diary; REVOKE ALL ON SCHEMA diary FROM PUBLIC; CREATE TABLE diary.canary(secret text);")
    migration=(ROOT/'scripts/knowledge/001-knowledge.sql').read_text(encoding='utf-8')
    # A failure halfway through must roll back every created object.
    broken=migration.replace('CREATE TABLE knowledge.mutation','SELECT 1/0;\nCREATE TABLE knowledge.mutation',1)
    assert sql(broken,False).returncode!=0
    assert sql("SELECT to_regnamespace('knowledge') IS NULL").stdout.strip()=='t'
    sql(migration);sql(migration)
    image_migration=(ROOT/'scripts/knowledge/002-private-images.sql').read_text(encoding='utf-8')
    sql(image_migration);sql(image_migration)
    env=os.environ.copy();env['BLOG_KNOWLEDGE_QA']='true'
    missing=pathlib.Path(os.environ.get('TEMP','.'))/('qa-missing-'+uuid.uuid4().hex)
    jvm=['-DargLine=-Djdk.net.unixdomain.tmpdir='+missing.as_posix()] if os.name=='nt' else []
    r=subprocess.run(['mvn.cmd' if os.name=='nt' else 'mvn','-q',*jvm,'-Dtest=KnowledgeStorePostgresTest','test'],cwd=ROOT/'blog-backend',env=env,capture_output=True,text=True,encoding='utf-8',errors='replace')
    if r.returncode: raise RuntimeError(r.stdout[-6000:]+r.stderr[-2000:])
    sql('ALTER TABLE knowledge.entry ADD COLUMN unexpected text')
    assert sql(migration,False).returncode!=0
    print(json.dumps(dict(migrationRepeat=True,transactionalMigration=True,driftRejected=True,postgresTestsPassed=True)))
finally:
    if receipt:
        current=json.loads(run(['docker','inspect',NAME]).stdout)[0]
        assert current['Id']==receipt['Id'] and current['Config']['Labels']['codex.change']==LABEL
        run(['docker','rm','-f',NAME])
