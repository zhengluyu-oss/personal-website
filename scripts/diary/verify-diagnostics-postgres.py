"""Disposable local-only PostgreSQL verification; never reads production config."""
import json, os, pathlib, subprocess, uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
IMAGE = 'pgvector/pgvector@sha256:131dcf7ff6a900545df8e7e092c270aa8c6db2f2c818e408cb45ec21316b74e6'
NAME = 'luyu-diagnostics-qa-' + uuid.uuid4().hex[:10]
LABEL = 'fix-diary-ai-validation-diagnostics'
def run(parts, body=None, check=True):
    value = subprocess.run(parts, input=body, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if check and value.returncode:
        raise RuntimeError(value.stdout[-2500:] + value.stderr[-2500:])
    return value
def sql(body, runtime=False, check=True):
    args = ['docker', 'exec', '-i']
    if runtime: args += ['-e', 'PGPASSWORD=isolated-diary-runtime']
    return run(args + [NAME, 'psql', '-h', '127.0.0.1', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'diary_runtime' if runtime else 'postgres', '-d', 'diary_qa'], body, check)
receipt = None
try:
    run(['docker', 'run', '-d', '--name', NAME, '--label', 'codex.change=' + LABEL, '--memory', '512m', '--cpus', '1', '-p', '127.0.0.1:15432:5432', '-e', 'POSTGRES_PASSWORD=isolated-diary-admin', '-e', 'POSTGRES_DB=diary_qa', IMAGE])
    receipt = json.loads(run(['docker', 'inspect', NAME]).stdout)[0]
    import time
    for _ in range(100):
        if sql('SELECT 1', check=False).returncode == 0: break
        time.sleep(.2)
    else: raise RuntimeError('QA database not ready')
    sql((ROOT / 'scripts/diary/000-roles.sql').read_text(encoding='utf-8'))
    sql("ALTER DATABASE diary_qa OWNER TO diary_migrator; ALTER ROLE diary_runtime LOGIN PASSWORD 'isolated-diary-runtime'; REVOKE ALL ON SCHEMA public FROM PUBLIC;")
    for name in ['001-private-diary.sql', '000-vector.sql', '002-private-diary-ai.sql', '003-private-diary-coaching.sql', '004-validation-diagnostics.sql', '004-validation-diagnostics.sql', '002-private-diary-ai.sql', '003-private-diary-coaching.sql']:
        sql((ROOT / 'scripts/diary' / name).read_text(encoding='utf-8'))
    assert sql("SELECT count(*) FROM diary_diagnostics.failure", True).stdout.strip() == '0'
    bad = sql("INSERT INTO diary_diagnostics.failure(owner_id,job_id,kind,reason,field_path,model,prompt_version,retention) VALUES(123,'00000000-0000-0000-0000-000000000001','REVIEW','JSON_PARSE','$','fixture','fixture','NOT_RETAINED')", True, False)
    assert bad.returncode != 0
    env = os.environ.copy(); env['BLOG_DIARY_AI_QA'] = 'true'
    # Force the JDK's documented-in-source TCP fallback on Windows hosts where AF_UNIX connect is broken.
    # The directory must not exist; no JDK/OS/production settings are changed.
    missing = pathlib.Path(os.environ.get('TEMP', '.')) / ('codex-qa-no-unix-' + uuid.uuid4().hex)
    assert not missing.exists()
    jvm = ['-DargLine=-Djdk.net.unixdomain.tmpdir=' + missing.as_posix()] if os.name == 'nt' else []
    result = subprocess.run(['mvn.cmd' if os.name == 'nt' else 'mvn', '-q', *jvm, '-Dtest=DiaryDiagnosticsPostgresTest,DiaryAiFailurePostgresTest,DiaryAiPostgresTest,CoachingPeriodAiTest', 'test'], cwd=ROOT / 'blog-backend', env=env, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if result.returncode: raise RuntimeError(result.stdout[-4000:] + result.stderr[-2000:])
    print(json.dumps({'migrationRepeat': True, 'oldSchemasUnchanged': True, 'noOwnerDenied': True, 'postgresTestsPassed': True}))
finally:
    if receipt:
        current = json.loads(run(['docker', 'inspect', NAME]).stdout)[0]
        assert current['Id'] == receipt['Id'] and current['Config']['Labels']['codex.change'] == LABEL
        run(['docker', 'rm', '-f', NAME])
