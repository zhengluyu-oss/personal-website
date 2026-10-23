"""Isolated metadata restore drill. Uses only disposable Docker databases."""
import json
import pathlib
import subprocess
import time
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
SUFFIX = uuid.uuid4().hex[:10]
PG = "luyu-knowledge-restore-pg-" + SUFFIX
MYSQL = "luyu-knowledge-restore-mysql-" + SUFFIX
LABEL = "add-knowledge-library-and-connections"
PG_IMAGE = "pgvector/pgvector@sha256:131dcf7ff6a900545df8e7e092c270aa8c6db2f2c818e408cb45ec21316b74e6"


def run(args, body=None, check=True):
    result = subprocess.run(args, input=body, capture_output=True)
    if check and result.returncode:
        raise RuntimeError((result.stdout + result.stderr)[-4000:].decode("utf-8", "replace"))
    return result.stdout


def pg(script, db="knowledge_source", check=True):
    return run(["docker", "exec", "-i", PG, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", db], script.encode("utf-8"), check)


def mysql(script, db="knowledge_source", check=True):
    return run(["docker", "exec", "-i", MYSQL, "mysql", "-uroot", "-pisolated-knowledge-restore", db], script.encode("utf-8"), check)


def check_row(sql, expected, db):
    got = pg(sql, db).decode().strip()
    if got != expected:
        raise RuntimeError(f"{db} mismatch: {got} != {expected}")


def main():
    created = []
    try:
        for name, image, env in [
            (PG, PG_IMAGE, ["-e", "POSTGRES_PASSWORD=isolated-knowledge-restore", "-e", "POSTGRES_DB=knowledge_source"]),
            (MYSQL, "mysql:8.4", ["-e", "MYSQL_ROOT_PASSWORD=isolated-knowledge-restore", "-e", "MYSQL_DATABASE=knowledge_source"]),
        ]:
            cid = run(["docker", "run", "-d", "--name", name, "--label", "codex.change=" + LABEL,
                       "--memory", "768m", "--cpus", "1", *env, image]).decode().strip()
            created.append((name, cid))
        for _ in range(120):
            pg_ready = pg("SELECT 1", check=False).strip().splitlines()[-1:] == [b"1"]
            mysql_ready = mysql("SELECT 1", check=False).strip().splitlines()[-1:] == [b"1"]
            if pg_ready and mysql_ready:
                break
            time.sleep(.5)
        else:
            raise RuntimeError("isolated databases unavailable")

        pg((ROOT / "scripts/knowledge/000-roles.sql").read_text(encoding="utf-8"))
        pg("ALTER DATABASE knowledge_source OWNER TO knowledge_migrator;")
        pg((ROOT / "scripts/knowledge/001-knowledge.sql").read_text(encoding="utf-8"))
        pg((ROOT / "scripts/knowledge/002-private-images.sql").read_text(encoding="utf-8"))
        pg((ROOT / "scripts/knowledge/002-private-images.sql").read_text(encoding="utf-8"))
        mysql((ROOT / "sql/knowledge-article-export.sql").read_text(encoding="utf-8"))
        seed_pg = """
INSERT INTO knowledge.entry(owner_id,id,kind,title,body,revision)
VALUES (9001,md5('a')::uuid,'NOTE','A','first',2),(9001,md5('b')::uuid,'CONCEPT','B','second',1);
INSERT INTO knowledge.entry_revision(owner_id,entry_id,revision,content,saved_at,action)
VALUES (9001,md5('a')::uuid,1,'{"body":"original"}',now(),'SAVE'),
       (9001,md5('a')::uuid,2,'{"body":"first"}',now(),'SAVE');
INSERT INTO knowledge.file_version(owner_id,id,entry_id,entry_revision,object_key,filename,mime,sha256,size_bytes,state)
VALUES (9001,md5('file')::uuid,md5('a')::uuid,2,'knowledge/private/objects/restore-fixture',
        'fixture.txt','text/plain',repeat('a',64),10,'CLAIMED');
UPDATE knowledge.entry SET current_file_version=md5('file')::uuid WHERE owner_id=9001 AND id=md5('a')::uuid;
INSERT INTO knowledge.image(owner_id,id,entry_id,mutation_id,request_hash,object_key,mime,size_bytes,width,height,state)
VALUES (9001,md5('image')::uuid,md5('a')::uuid,md5('image-request')::uuid,repeat('c',64),
        'knowledge/private/objects/restore-image','image/png',128,8,8,'ACTIVE');
INSERT INTO knowledge.relation(owner_id,id,from_kind,from_id,to_kind,to_id,type,note,revision)
VALUES (9001,md5('relation')::uuid,'ENTRY',md5('a')::uuid::text,'ENTRY',md5('b')::uuid::text,'EXPLAINS','sample',1);
INSERT INTO knowledge.article_export(owner_id,id,request_hash,snapshot,state,article_id)
VALUES (9001,md5('export')::uuid,repeat('b',64),'{"title":"draft"}','CREATED',123);
"""
        pg(seed_pg)
        mysql("INSERT INTO t_knowledge_article_export(owner_id,export_id,request_hash,article_id) VALUES(9001,'11111111-1111-1111-1111-111111111111',REPEAT('b',64),123);")
        inventory_sql = "SELECT object_key,sha256,size_bytes,state FROM knowledge.file_version WHERE owner_id=9001 UNION ALL SELECT object_key,'' AS sha256,size_bytes,state FROM knowledge.image WHERE owner_id=9001 ORDER BY object_key;"
        inventory = pg(inventory_sql)
        if b"knowledge/private/objects/restore-fixture" not in inventory:
            raise RuntimeError("private object inventory missing")
        if b"knowledge/private/objects/restore-image" not in inventory:
            raise RuntimeError("private image inventory missing")

        dump_pg = run(["docker", "exec", PG, "pg_dump", "-U", "postgres", "-Fc", "-n", "knowledge", "knowledge_source"])
        dump_mysql = run(["docker", "exec", MYSQL, "mysqldump", "-uroot", "-pisolated-knowledge-restore",
                          "--single-transaction", "--no-tablespaces", "--set-gtid-purged=OFF",
                          "knowledge_source", "t_knowledge_article_export"])
        run(["docker", "exec", PG, "createdb", "-U", "postgres", "knowledge_restored"])
        run(["docker", "exec", "-i", PG, "pg_restore", "-U", "postgres", "-d", "knowledge_restored"], dump_pg)
        mysql("CREATE DATABASE knowledge_restored;")
        run(["docker", "exec", "-i", MYSQL, "mysql", "-uroot", "-pisolated-knowledge-restore", "knowledge_restored"], dump_mysql)

        for query, expected in [
            ("SELECT count(*) FROM knowledge.entry;", "2"),
            ("SELECT count(*) FROM knowledge.entry_revision;", "2"),
            ("SELECT count(*) FROM knowledge.relation;", "1"),
            ("SELECT count(*) FROM knowledge.article_export WHERE article_id=123;", "1"),
            ("SELECT count(*) FROM knowledge.file_version;", "1"),
            ("SELECT count(*) FROM knowledge.image;", "1"),
            ("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='knowledge' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity;", "13"),
        ]:
            check_row(query, expected, "knowledge_restored")
        if pg(inventory_sql, "knowledge_restored") != inventory:
            raise RuntimeError("restored object inventory differs")
        if mysql("SELECT COUNT(*) FROM t_knowledge_article_export WHERE article_id=123;", "knowledge_restored").strip().splitlines()[-1:] != [b"1"]:
            raise RuntimeError("MySQL export mapping missing")
        check_row("BEGIN; SET ROLE knowledge_runtime; SELECT count(*) FROM knowledge.entry; COMMIT;", "0", "knowledge_restored")
        check_row("BEGIN; SET ROLE knowledge_runtime; SELECT set_config('knowledge.owner_id','9001',true); SELECT count(*) FROM knowledge.entry; COMMIT;", "9001\n2", "knowledge_restored")
        pg("CREATE SCHEMA diary; CREATE TABLE diary.canary(secret text);")
        denied = pg("BEGIN; SET ROLE knowledge_runtime; SELECT * FROM diary.canary;", "knowledge_restored", check=False)
        if denied == b"":
            # psql returns no rows on permission error; inspect exit status separately.
            status = subprocess.run(["docker", "exec", "-i", PG, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "knowledge_restored"],
                                    input=b"BEGIN; SET ROLE knowledge_runtime; SELECT * FROM diary.canary;", capture_output=True)
            if status.returncode == 0:
                raise RuntimeError("knowledge runtime can read diary")
        print(json.dumps({"postgresCustomDumpBytes": len(dump_pg), "mysqlSqlDumpBytes": len(dump_mysql),
                          "history": 2, "relations": 1, "exports": 1, "privateObjectManifest": 2,
                          "forcedRlsTables": 13, "runtimeWithoutOwnerRows": 0,
                          "runtimeWithOwnerRows": 2, "diaryDenied": True}, ensure_ascii=False))
    finally:
        for name, cid in created:
            inspected = run(["docker", "inspect", name], check=False)
            if inspected:
                details = json.loads(inspected)[0]
                if details["Id"] == cid and details["Config"]["Labels"].get("codex.change") == LABEL:
                    run(["docker", "rm", "-f", name])


if __name__ == "__main__":
    main()
