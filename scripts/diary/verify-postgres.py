"""显式 opt-in 的隔离 PostgreSQL 迁移/RLS/恢复测试，不读取任何应用配置。

运行：python scripts/diary/verify-postgres.py
只操作本进程新建并带专属标签的容器；不安装 vector 扩展、不连接生产。
"""
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parent
IMAGE = "pgvector/pgvector@sha256:131dcf7ff6a900545df8e7e092c270aa8c6db2f2c818e408cb45ec21316b74e6"
NAME = f"diary-postgres-qa-{os.getpid()}"
LABEL = "codex.change=add-private-diary-postgres"


def command(*args, input=None, expect=True):
    result = subprocess.run(args, input=input, text=True, encoding="utf-8",
                            capture_output=True, timeout=60)
    if expect and result.returncode:
        raise AssertionError(f"Test command failed: {result.stderr[-1500:]}")
    return result


def sql(statement, database="diary_qa", runtime=False, expect=True):
    args = ["docker", "exec", "-i"]
    if runtime:
        args += ["-e", "PGPASSWORD=isolated-diary-runtime"]
    args += [NAME, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1"]
    args += ["-h", "127.0.0.1", "-U", "diary_runtime"] if runtime else ["-U", "postgres"]
    return command(*args, "-d", database, input=statement, expect=expect)


def check(statement, expected, **kwargs):
    actual = sql(statement, **kwargs).stdout.strip()
    assert actual == expected, f"Expected {expected!r}; got {actual!r}"


def context(owner):
    return f"BEGIN; SELECT set_config('diary.owner_id','{owner}',true);"


def main():
    migration = (ROOT / "001-private-diary.sql").read_text(encoding="utf-8")
    identifier = None
    try:
        identifier = command("docker", "run", "--name", NAME, "--label", LABEL,
                             "--memory", "384m", "--cpus", "1", "-e",
                             "POSTGRES_PASSWORD=isolated-diary-admin", "-e",
                             "POSTGRES_DB=diary_qa", "-d", IMAGE).stdout.strip()
        for _ in range(60):
            ready = command("docker", "exec", NAME, "pg_isready", "-h", "127.0.0.1", "-U", "postgres", expect=False)
            if ready.returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise AssertionError("Isolated PostgreSQL did not become ready")

        sql((ROOT / "000-roles.sql").read_text(encoding="utf-8"))
        sql("ALTER DATABASE diary_qa OWNER TO diary_migrator;")
        sql(migration)
        sql(migration)
        check("SELECT version FROM diary.schema_version", "1")
        check("SELECT count(*) FROM pg_extension WHERE extname='vector'", "0")
        sql("ALTER ROLE diary_runtime LOGIN PASSWORD 'isolated-diary-runtime';")
        check("SELECT current_user", "diary_runtime", runtime=True)
        assert sql("CREATE TABLE diary.not_allowed(id int)", runtime=True, expect=False).returncode != 0
        check("SELECT count(*) FROM diary.entry", "0", runtime=True)
        assert sql("INSERT INTO diary.entry(id,owner_id,title,body,revision) VALUES "
                   "('00000000-0000-4000-8000-000000000099',7,'x','x',1)",
                   runtime=True, expect=False).returncode != 0

        fixture = """
INSERT INTO diary.entry(id,owner_id,title,body,covered_from,covered_to,revision) VALUES
 ('00000000-0000-4000-8000-000000000001',7,'synthetic A','synthetic 中文🙂', '2026-10-01','2026-10-15',1),
 ('00000000-0000-4000-8000-000000000002',7,'synthetic B','same writing day',NULL,NULL,1);
INSERT INTO diary.entry_revision(entry_id,owner_id,revision,title,body,covered_from,covered_to,action,deleted)
 SELECT id,owner_id,revision,title,body,covered_from,covered_to,'CREATE',false FROM diary.entry;
INSERT INTO diary.mutation(owner_id,mutation_id,entry_id,action,request_digest,result_revision,saved_at)
 VALUES(7,'00000000-0000-4000-8000-000000000011','00000000-0000-4000-8000-000000000001',
 'CREATE',repeat('a',64),1,transaction_timestamp());
"""
        sql(context(7) + fixture + "COMMIT;", runtime=True)
        check(context(7) + "SELECT count(*) FROM diary.entry; COMMIT;", "7\n2", runtime=True)
        # 同一连接跨提交/回滚不残留归属，上下文缺失默认拒绝，三个表均验证。
        for table in ("entry", "entry_revision", "mutation"):
            check(context(7) + "COMMIT; SELECT count(*) FROM diary." + table, "7\n0", runtime=True)
            check(context(7) + "ROLLBACK; SELECT count(*) FROM diary." + table, "7\n0", runtime=True)
            check(context(8) + "SELECT count(*) FROM diary." + table + "; COMMIT;", "8\n0", runtime=True)
        assert sql(context(8) + "UPDATE diary.entry SET owner_id=8; COMMIT;", runtime=True).returncode == 0
        check(context(7) + "SELECT count(*) FROM diary.entry; COMMIT;", "7\n2", runtime=True)
        invalid = context(7) + "INSERT INTO diary.entry(id,owner_id,title,body,covered_from,revision) VALUES "
        invalid += "('00000000-0000-4000-8000-000000000099',7,'x','x','2026-10-01',1); COMMIT;"
        assert sql(invalid, runtime=True, expect=False).returncode != 0
        for table in ("entry_revision", "mutation"):
            assert sql(context(7) + f"UPDATE diary.{table} SET owner_id=8; COMMIT;",
                       runtime=True, expect=False).returncode != 0

        # 修改、删除与幂等结果也进入备份；所有操作仅用虚构测试数据。
        sql(context(7) + """
UPDATE diary.entry SET revision=2,body='synthetic amended',updated_at=transaction_timestamp()
 WHERE id='00000000-0000-4000-8000-000000000001' AND owner_id=7 AND revision=1;
INSERT INTO diary.entry_revision(entry_id,owner_id,revision,title,body,covered_from,covered_to,action,deleted)
 SELECT id,owner_id,revision,title,body,covered_from,covered_to,'SAVE',false FROM diary.entry WHERE revision=2;
UPDATE diary.entry SET revision=2,deleted_at=transaction_timestamp(),updated_at=transaction_timestamp()
 WHERE id='00000000-0000-4000-8000-000000000002' AND owner_id=7 AND revision=1;
INSERT INTO diary.entry_revision(entry_id,owner_id,revision,title,body,covered_from,covered_to,action,deleted)
 SELECT id,owner_id,revision,title,body,covered_from,covered_to,'DELETE',true FROM diary.entry WHERE deleted_at IS NOT NULL;
COMMIT;
""", runtime=True)
        # 失败事务不会留下正文或半套迁移。
        assert sql(context(7) + "UPDATE diary.entry SET body='must rollback'; SELECT 1/0; COMMIT;",
                   runtime=True, expect=False).returncode != 0
        check(context(7) + "SELECT count(*) FROM diary.entry WHERE body='must rollback'; COMMIT;", "7\n0", runtime=True)
        sql("CREATE DATABASE diary_failed OWNER diary_migrator;")
        broken = migration.replace("COMMIT;", "SELECT 1/0;\nCOMMIT;")
        assert sql(broken, database="diary_failed", expect=False).returncode != 0
        check("SELECT count(*) FROM pg_namespace WHERE nspname='diary'", "0", database="diary_failed")
        sql("CREATE DATABASE diary_bad OWNER diary_migrator;")
        sql("CREATE SCHEMA diary AUTHORIZATION diary_migrator;", database="diary_bad")
        assert sql(migration, database="diary_bad", expect=False).returncode != 0
        sql("ALTER TABLE diary.entry ADD COLUMN unexpected text;")
        assert sql(migration, expect=False).returncode != 0
        sql("ALTER TABLE diary.entry DROP COLUMN unexpected;")
        sql(migration)

        snapshot_query = """
SELECT jsonb_build_object(
 'entries',(SELECT jsonb_agg(to_jsonb(e) ORDER BY id) FROM diary.entry e),
 'history',(SELECT jsonb_agg(to_jsonb(h) ORDER BY entry_id,revision) FROM diary.entry_revision h),
 'mutations',(SELECT jsonb_agg(to_jsonb(m) ORDER BY mutation_id) FROM diary.mutation m),
 'schema',(SELECT jsonb_agg(to_jsonb(v)) FROM diary.schema_version v));
"""
        before = json.loads(sql(snapshot_query).stdout)
        command("docker", "restart", NAME)
        for _ in range(60):
            if command("docker", "exec", NAME, "pg_isready", "-h", "127.0.0.1", "-U", "postgres", expect=False).returncode == 0:
                break
            time.sleep(0.5)
        assert json.loads(sql(snapshot_query).stdout) == before
        command("docker", "exec", NAME, "pg_dump", "-U", "postgres", "-d", "diary_qa", "-Fc", "-f", "/tmp/diary.dump")
        sql("CREATE DATABASE diary_restored OWNER diary_migrator;")
        started = time.monotonic()
        command("docker", "exec", NAME, "pg_restore", "-U", "postgres", "--exit-on-error", "-d", "diary_restored", "/tmp/diary.dump")
        assert json.loads(sql(snapshot_query, database="diary_restored").stdout) == before
        sql(migration, database="diary_restored")
        check(context(8) + "SELECT count(*) FROM diary.entry; COMMIT;", "8\n0", runtime=True, database="diary_restored")
        check(context(7) + "SELECT count(*) FROM diary.entry; COMMIT;", "7\n2", runtime=True, database="diary_restored")
        check("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
              "WHERE n.nspname='diary' AND c.relname IN ('entry','entry_revision','mutation') "
              "AND c.relrowsecurity AND c.relforcerowsecurity", "3", database="diary_restored")
        print(json.dumps({"status": "passed", "postgres": sql("SHOW server_version").stdout.strip(),
                          "image": IMAGE, "restoreSeconds": round(time.monotonic()-started, 3),
                          "checks": ["initial/repeated migration", "runtime role/RLS on all tables",
                                     "same-day entries", "nullable date range constraint", "immutable history",
                                     "transaction rollback", "migration failure rollback", "incompatible schema refusal",
                                     "restart persistence", "pg_restore exact snapshots and isolation"],
                          "notCovered": ["application/JDBC concurrency", "API mutation replay", "encrypted production backups"]},
                         ensure_ascii=False))
    finally:
        if identifier:
            label = command("docker", "inspect", "--format", '{{index .Config.Labels "codex.change"}}', identifier).stdout.strip()
            assert label == "add-private-diary-postgres", "Refuse cleanup of foreign container"
            command("docker", "rm", "-fv", identifier)


if __name__ == "__main__":
    main()
