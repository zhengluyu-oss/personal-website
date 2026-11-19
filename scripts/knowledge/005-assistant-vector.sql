\set ON_ERROR_STOP on
BEGIN;
GRANT USAGE ON SCHEMA diary_vector TO knowledge_migrator,knowledge_runtime;
GRANT USAGE ON TYPE diary_vector.vector TO knowledge_migrator,knowledge_runtime;
SET LOCAL ROLE knowledge_migrator;
SET LOCAL statement_timeout='30s';
SELECT pg_advisory_xact_lock(1936876915,5);
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM knowledge.schema_version WHERE version=4) THEN RAISE EXCEPTION 'Knowledge assistant v4 required'; END IF;
 IF to_regtype('diary_vector.vector') IS NULL THEN RAISE EXCEPTION 'pgvector is required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM knowledge.schema_version WHERE version=5)
    AND (to_regclass('knowledge_assistant.vector_entry') IS NOT NULL OR to_regclass('knowledge_assistant.vector_chunk') IS NOT NULL)
    THEN RAISE EXCEPTION 'Unversioned assistant vector tables'; END IF;
END $$;
CREATE TABLE IF NOT EXISTS knowledge_assistant.vector_entry (
 owner_id bigint NOT NULL, entry_id uuid NOT NULL, source_revision bigint NOT NULL,
 file_version uuid, file_ready boolean NOT NULL, profile text NOT NULL,
 next_chunk integer NOT NULL DEFAULT 0 CHECK(next_chunk>=0),
 total_chunks integer NOT NULL DEFAULT 0 CHECK(total_chunks>=0),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 3),
 status text NOT NULL CHECK(status IN ('RUNNING','READY','FAILED')),
 retry_after timestamptz, updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(owner_id,entry_id),
 FOREIGN KEY(owner_id,entry_id) REFERENCES knowledge.entry(owner_id,id)
);
CREATE TABLE IF NOT EXISTS knowledge_assistant.vector_chunk (
 owner_id bigint NOT NULL, entry_id uuid NOT NULL, chunk_index integer NOT NULL CHECK(chunk_index>=0),
 source_revision bigint NOT NULL, file_version uuid, profile text NOT NULL,
 source_kind text NOT NULL CHECK(source_kind IN ('BODY','FILE')),
 page integer, from_line integer, to_line integer,
 excerpt text NOT NULL CHECK(length(excerpt) BETWEEN 1 AND 1200),
 dimensions integer NOT NULL CHECK(dimensions BETWEEN 1 AND 4096),
 embedding diary_vector.vector NOT NULL,
 PRIMARY KEY(owner_id,entry_id,chunk_index),
 FOREIGN KEY(owner_id,entry_id) REFERENCES knowledge.entry(owner_id,id),
 CHECK(diary_vector.vector_dims(embedding)=dimensions),
 CHECK(diary_vector.vector_norm(embedding)>0)
);
CREATE INDEX IF NOT EXISTS assistant_vector_profile ON knowledge_assistant.vector_chunk(owner_id,profile,dimensions);
CREATE INDEX IF NOT EXISTS assistant_vector_entry_state ON knowledge_assistant.vector_entry(owner_id,status,retry_after,updated_at);
ALTER TABLE knowledge_assistant.vector_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_assistant.vector_entry FORCE ROW LEVEL SECURITY;
ALTER TABLE knowledge_assistant.vector_chunk ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_assistant.vector_chunk FORCE ROW LEVEL SECURITY;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='knowledge_assistant' AND tablename='vector_entry' AND policyname='knowledge_owner') THEN
  CREATE POLICY knowledge_owner ON knowledge_assistant.vector_entry TO knowledge_runtime
   USING(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint)
   WITH CHECK(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint);
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='knowledge_assistant' AND tablename='vector_chunk' AND policyname='knowledge_owner') THEN
  CREATE POLICY knowledge_owner ON knowledge_assistant.vector_chunk TO knowledge_runtime
   USING(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint)
   WITH CHECK(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint);
 END IF;
END $$;
REVOKE ALL ON knowledge_assistant.vector_entry,knowledge_assistant.vector_chunk FROM PUBLIC,knowledge_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON knowledge_assistant.vector_entry,knowledge_assistant.vector_chunk TO knowledge_runtime;
DO $$ DECLARE actual text; BEGIN
 SELECT md5(string_agg(def,E'\n' ORDER BY def)) INTO actual FROM (
  SELECT 'column:'||c.relname||':'||a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull AS def
   FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
   WHERE n.nspname='knowledge_assistant' AND c.relname IN ('vector_entry','vector_chunk')
  UNION ALL SELECT 'constraint:'||c.relname||':'||pg_get_constraintdef(k.oid)
   FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='knowledge_assistant' AND c.relname IN ('vector_entry','vector_chunk')
  UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='knowledge_assistant' AND tablename IN ('vector_entry','vector_chunk')
  UNION ALL SELECT 'policy:'||tablename||':'||policyname||':'||roles::text||':'||cmd||':'||qual||':'||with_check
   FROM pg_policies WHERE schemaname='knowledge_assistant' AND tablename IN ('vector_entry','vector_chunk')
  UNION ALL SELECT 'table:'||c.relname||':'||c.relrowsecurity||':'||c.relforcerowsecurity||':'||r.rolname||':'||coalesce(c.relacl::text,'')
   FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner
   WHERE n.nspname='knowledge_assistant' AND c.relname IN ('vector_entry','vector_chunk') AND c.relkind='r'
 ) shape;
 INSERT INTO knowledge.schema_version(version,fingerprint) VALUES(5,actual) ON CONFLICT(version) DO NOTHING;
 IF (SELECT fingerprint FROM knowledge.schema_version WHERE version=5) IS DISTINCT FROM actual THEN RAISE EXCEPTION 'Assistant vector schema drift'; END IF;
END $$;
COMMIT;
