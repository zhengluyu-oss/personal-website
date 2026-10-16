"""Only the explicitly labeled disposable fixture; never resolves application secrets."""
import json, pathlib, subprocess, uuid

ROOT = pathlib.Path(__file__).resolve().parent
CONTAINER = 'luyu-diary-ai-qa-20261015'
meta = json.loads(subprocess.check_output(['docker', 'inspect', CONTAINER]))[0]
assert meta['Config']['Labels'].get('codex.change') == 'add-diary-rag-and-ai-review'
assert meta['HostConfig']['Memory'] == 512 * 1024 * 1024

def sql(value, database='diary_qa', runtime=False, ok=True):
    command = ['docker', 'exec', '-i', '-e', 'PGPASSWORD=isolated-diary-runtime', meta['Id'], 'psql', '-h', '127.0.0.1', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'diary_runtime' if runtime else 'postgres', '-d', database]
    result = subprocess.run(command, input=('\\set VERBOSITY sqlstate\n' + value).encode(), capture_output=True, timeout=90)
    if ok and result.returncode:
        raise AssertionError(result.stderr.decode(errors='replace')[-1000:])
    return result

def migration(name, database):
    return sql((ROOT / name).read_text(encoding='utf8'), database)

script = (ROOT / '003-private-diary-coaching.sql').read_text(encoding='utf8')
for name in ['003-private-diary-coaching.sql', '003-private-diary-coaching.sql', '001-private-diary.sql', '002-private-diary-ai.sql']:
    migration(name, 'diary_qa')
database = 'coaching_core_' + uuid.uuid4().hex[:12]
sql('CREATE DATABASE ' + database + ' OWNER diary_migrator', 'postgres')
migration('001-private-diary.sql', database)
assert sql(script.replace('CREATE TABLE diary_coaching.origin_link(', 'SELECT 1/0;\nCREATE TABLE diary_coaching.origin_link('), database, ok=False).returncode
assert sql("SELECT to_regnamespace('diary_coaching') IS NULL", database).stdout.strip() == b't'
for _ in range(2): migration('003-private-diary-coaching.sql', database)
sql('ALTER TABLE diary_coaching.goal ADD COLUMN fictional_drift integer', database)
assert sql(script, database, ok=False).returncode
sql('ALTER TABLE diary_coaching.goal DROP COLUMN fictional_drift', database)
migration('003-private-diary-coaching.sql', database); migration('001-private-diary.sql', database)
assert sql("SELECT to_regnamespace('diary_ai') IS NULL", database).stdout.strip() == b't'
assert sql("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='diary_coaching' AND c.relrowsecurity AND c.relforcerowsecurity", database).stdout.strip() == b'12'
goal1, goal2, action = [str(uuid.uuid4()) for _ in range(3)]
setup = f"""BEGIN;SELECT set_config('diary.owner_id','301',true);
INSERT INTO diary_coaching.goal VALUES(301,'{goal1}',NULL,1,'{{"title":"fictional","state":"ACTIVE"}}',now(),now(),NULL);
SELECT set_config('diary.owner_id','302',true);
INSERT INTO diary_coaching.goal VALUES(302,'{goal2}',NULL,1,'{{"title":"fictional other","state":"ACTIVE"}}',now(),now(),NULL);
SELECT set_config('diary.owner_id','301',true);
"""
assert sql(setup + "SELECT count(*) FROM diary_coaching.goal;ROLLBACK;SELECT count(*) FROM diary_coaching.goal;", database, runtime=True).stdout.decode().strip().splitlines()[-2:] == ['1', '0']
foreign = f"INSERT INTO diary_coaching.action VALUES(301,'{action}','{goal2}',1,'{{\"text\":\"fictional\",\"state\":\"PLANNED\"}}',now(),now(),NULL);"
assert '23503' in sql(setup + foreign, database, runtime=True, ok=False).stderr.decode()
assert '42501' in sql(f"INSERT INTO diary_coaching.goal VALUES(301,'{goal1}',NULL,1,'{{\"title\":\"fictional\",\"state\":\"ACTIVE\"}}',now(),now(),NULL)", database, runtime=True, ok=False).stderr.decode()
assert sql("SELECT has_table_privilege(current_user,'diary_coaching.goal_revision','UPDATE'),has_table_privilege(current_user,'diary_coaching.mutation','DELETE'),has_schema_privilege(current_user,'diary_coaching','CREATE')", database, runtime=True).stdout.strip() == b'f|f|f'
print(json.dumps({'status': 'passed', 'coreOnlyDatabase': database, 'combinedAndCoreOnly': True, 'forceRlsTables': 12, 'atomicFailure': True, 'driftRefused': True, 'ownerCompositeFk': True, 'transactionLocalOwner': True, 'oldFingerprints': True}))
