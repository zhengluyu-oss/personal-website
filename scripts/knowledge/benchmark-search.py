"""Reproducible isolated PostgreSQL search sample; never loads production config."""
import json
import pathlib
import re
import statistics
import subprocess
import time
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
NAME = "luyu-knowledge-bench-" + uuid.uuid4().hex[:10]
IMAGE = "pgvector/pgvector@sha256:131dcf7ff6a900545df8e7e092c270aa8c6db2f2c818e408cb45ec21316b74e6"
LABEL = "add-knowledge-library-and-connections"
TERM_HEX = "知识关联".encode("utf-8").hex()
TERM_SQL = "convert_from(decode('" + TERM_HEX + "','hex'),'UTF8')"


def run(args, body=None, check=True):
    result = subprocess.run(args, input=body, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and result.returncode:
        raise RuntimeError(result.stdout[-3000:] + result.stderr[-3000:])
    return result


def sql(body, check=True):
    return run(["docker", "exec", "-i", NAME, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "knowledge_bench"], body, check)


QUERY = """
SELECT count(*) FROM knowledge.entry e
WHERE e.owner_id=9000 AND e.deleted_at IS NULL
  AND (position(lower('知识关联') in lower(e.title))>0
    OR position(lower('知识关联') in lower(e.body))>0
    OR EXISTS(SELECT FROM knowledge.entry_tag et JOIN knowledge.label l ON l.owner_id=et.owner_id AND l.id=et.tag_id
              WHERE et.owner_id=e.owner_id AND et.entry_id=e.id AND position(lower('知识关联') in lower(l.name))>0)
    OR EXISTS(SELECT FROM knowledge.extracted_segment s JOIN knowledge.processing_job j
              ON j.owner_id=s.owner_id AND j.file_version=s.file_version AND j.state='READY'
              WHERE s.owner_id=e.owner_id AND s.entry_id=e.id AND s.file_version=e.current_file_version
                AND position(lower('知识关联') in lower(s.body))>0));
""".replace("'知识关联'", TERM_SQL)


def main():
    container = None
    try:
        container = run(["docker", "run", "-d", "--name", NAME, "--label", "codex.change=" + LABEL,
                         "--memory", "512m", "--cpus", "1", "-e", "POSTGRES_PASSWORD=isolated-knowledge-bench",
                         "-e", "POSTGRES_DB=knowledge_bench", IMAGE]).stdout.strip()
        for _ in range(100):
            if sql("SELECT 1", False).returncode == 0:
                break
            time.sleep(.25)
        else:
            raise RuntimeError("isolated database did not start")
        sql((ROOT / "scripts/knowledge/000-roles.sql").read_text(encoding="utf-8"))
        sql("ALTER DATABASE knowledge_bench OWNER TO knowledge_migrator;")
        sql((ROOT / "scripts/knowledge/001-knowledge.sql").read_text(encoding="utf-8"))
        fixture = """
INSERT INTO knowledge.entry(owner_id,id,kind,title,body,revision)
SELECT 9000,md5('entry-'||i)::uuid,'DOCUMENT',
       CASE WHEN i%20=0 THEN '知识关联 资料 '||i ELSE '学习资料 '||i END,
       '中文学习记录 第'||i||'篇',1 FROM generate_series(1,1000) i;
INSERT INTO knowledge.file_version(owner_id,id,entry_id,entry_revision,object_key,filename,mime,sha256,size_bytes,state)
SELECT 9000,md5('file-'||i)::uuid,md5('entry-'||i)::uuid,1,
       'knowledge/private/objects/bench-'||i,'sample.txt','text/plain',repeat('a',64),100,'CLAIMED'
FROM generate_series(1,1000) i;
UPDATE knowledge.entry e SET current_file_version=f.id FROM knowledge.file_version f
WHERE f.owner_id=e.owner_id AND f.entry_id=e.id AND e.owner_id=9000;
INSERT INTO knowledge.processing_job(owner_id,id,entry_id,file_version,state)
SELECT 9000,md5('job-'||i)::uuid,md5('entry-'||i)::uuid,md5('file-'||i)::uuid,'READY'
FROM generate_series(1,1000) i;
INSERT INTO knowledge.extracted_segment(owner_id,entry_id,file_version,ordinal,page,body)
SELECT 9000,md5('entry-'||i)::uuid,md5('file-'||i)::uuid,n,n+1,
       CASE WHEN i%5=0 AND n=10 THEN '这里记录知识关联的例子 '||i ELSE '普通中文段落 '||i||' 页 '||n END
FROM generate_series(1,1000) i CROSS JOIN generate_series(0,19) n;
ANALYZE knowledge.entry;
ANALYZE knowledge.extracted_segment;
ANALYZE knowledge.processing_job;
"""
        fixture = fixture.replace("'知识关联 资料 '", TERM_SQL + "||' sample '")
        fixture = fixture.replace("'这里记录知识关联的例子 '", TERM_SQL + "||' segment '")
        sql(fixture)
        counts = sql("SELECT (SELECT count(*) FROM knowledge.entry WHERE owner_id=9000),(SELECT count(*) FROM knowledge.extracted_segment WHERE owner_id=9000);").stdout.strip()
        if counts != "1000|20000":
            raise RuntimeError("unexpected fixture counts: " + counts)
        title_hex = sql("SELECT encode(convert_to(title,'UTF8'),'hex') FROM knowledge.entry WHERE owner_id=9000 AND id=md5('entry-20')::uuid;").stdout.strip()
        if not title_hex.startswith(TERM_HEX):
            raise RuntimeError("fixture is not UTF-8 Chinese")
        # SET ROLE and SET LOCAL make RLS execute as the application role with the server-owned context.
        prefix = "BEGIN; SET ROLE knowledge_runtime; SELECT set_config('knowledge.owner_id','9000',true);\n"
        plan = sql(prefix + "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + QUERY + "COMMIT;").stdout
        values = []
        for _ in range(30):
            result = sql(prefix + "EXPLAIN (ANALYZE, FORMAT JSON) " + QUERY + "COMMIT;").stdout
            match = re.search(r"\[\s*\{.*\}\s*\]", result, re.S)
            if not match:
                raise RuntimeError("EXPLAIN JSON missing")
            values.append(json.loads(match.group(0))[0]["Execution Time"])
        values.sort()
        print(json.dumps({"entries": 1000, "segments": 20000, "postgresContainerMemoryMiB": 512,
                          "postgresContainerCpus": 1, "warmRuns": 30,
                          "p50Ms": round(statistics.median(values), 2), "p95Ms": round(values[28], 2),
                          "maxMs": round(values[-1], 2), "plan": plan.strip()}, ensure_ascii=False))
    finally:
        if container:
            current = run(["docker", "inspect", NAME], check=False)
            if current.returncode == 0:
                inspected = json.loads(current.stdout)[0]
                if inspected["Id"] == container and inspected["Config"]["Labels"].get("codex.change") == LABEL:
                    run(["docker", "rm", "-f", NAME])


if __name__ == "__main__":
    main()
