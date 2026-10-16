-- PG16 / pgvector。先执行 000-vector.sql；只新增 diary_ai，不修改 diary 原文结构。
BEGIN;
SET LOCAL ROLE diary_migrator;
SELECT pg_advisory_xact_lock(1936876914,2);
SET LOCAL statement_timeout='30s';
DO $$ BEGIN
 IF NOT EXISTS(SELECT FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace WHERE e.extname='vector' AND n.nspname='diary_vector') THEN
  RAISE EXCEPTION 'Install vetted vector extension explicitly first';
 END IF;
END $$;
SELECT to_regnamespace('diary_ai') IS NOT NULL AS ai_exists \gset
\if :ai_exists
DO $$ DECLARE actual text; expected text; BEGIN
 IF to_regclass('diary_ai.schema_version') IS NULL THEN RAISE EXCEPTION 'Unversioned AI schema'; END IF;
 SELECT fingerprint INTO expected FROM diary_ai.schema_version WHERE version=1;
 SELECT md5(string_agg(def,E'\n' ORDER BY def)) INTO actual FROM (
  SELECT 'column:'||c.relname||':'||a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull||':'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') AS def
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
  LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE n.nspname='diary_ai' AND c.relkind='r'
  UNION ALL SELECT 'constraint:'||c.relname||':'||pg_get_constraintdef(k.oid) FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='diary_ai'
  UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='diary_ai'
  UNION ALL SELECT 'policy:'||tablename||':'||policyname||':'||roles::text||':'||cmd||':'||qual||':'||with_check FROM pg_policies WHERE schemaname='diary_ai'
  UNION ALL SELECT 'table:'||c.relname||':'||c.relrowsecurity||':'||c.relforcerowsecurity||':'||r.rolname||':'||coalesce(c.relacl::text,'') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='diary_ai' AND c.relkind='r'
 ) shape;
 IF expected IS NULL OR expected<>actual THEN RAISE EXCEPTION 'AI schema drift; migration refused'; END IF;
END $$;
\else
CREATE SCHEMA diary_ai AUTHORIZATION diary_migrator;
REVOKE ALL ON SCHEMA diary_ai FROM PUBLIC;
GRANT USAGE ON SCHEMA diary_ai TO diary_runtime;
CREATE TABLE diary_ai.schema_version(version integer PRIMARY KEY CHECK(version=1),fingerprint text NOT NULL,installed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE diary_ai.embedding_profile(
 owner_id bigint NOT NULL CHECK(owner_id>0),id text NOT NULL CHECK(id~'^[0-9a-f]{64}$'),
 provider text NOT NULL,model text NOT NULL,dimensions integer NOT NULL CHECK(dimensions BETWEEN 1 AND 4096),chunker text NOT NULL,
 PRIMARY KEY(owner_id,id),UNIQUE(owner_id,id,dimensions));
CREATE TABLE diary_ai.owner_settings(
 owner_id bigint PRIMARY KEY CHECK(owner_id>0),syncing boolean NOT NULL DEFAULT false,profile_id text NOT NULL,
 FOREIGN KEY(owner_id,profile_id) REFERENCES diary_ai.embedding_profile(owner_id,id));
CREATE TABLE diary_ai.entry_index(
 owner_id bigint NOT NULL,entry_id uuid NOT NULL,profile_id text NOT NULL,source_revision bigint NOT NULL CHECK(source_revision>0),
 total_chunks integer NOT NULL CHECK(total_chunks>=0),next_chunk integer NOT NULL DEFAULT 0 CHECK(next_chunk>=0),
 status text NOT NULL CHECK(status IN('PENDING','RUNNING','READY','FAILED','UNCERTAIN','QUOTA')),error_code text,
 updated_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(owner_id,entry_id,profile_id),
 UNIQUE(owner_id,entry_id,profile_id,source_revision),
 FOREIGN KEY(entry_id,owner_id) REFERENCES diary.entry(id,owner_id),
 FOREIGN KEY(owner_id,profile_id) REFERENCES diary_ai.embedding_profile(owner_id,id));
CREATE TABLE diary_ai.chunk(
 owner_id bigint NOT NULL,entry_id uuid NOT NULL,profile_id text NOT NULL,source_revision bigint NOT NULL,
 chunk_index integer NOT NULL CHECK(chunk_index>=0),start_cp integer NOT NULL CHECK(start_cp>=0),end_cp integer NOT NULL,
 text_hash text NOT NULL CHECK(text_hash~'^[0-9a-f]{64}$'),dimensions integer NOT NULL,
 embedding diary_vector.vector NOT NULL,
 PRIMARY KEY(owner_id,entry_id,profile_id,chunk_index),CHECK(end_cp>start_cp),
 CHECK(diary_vector.vector_dims(embedding)=dimensions),CHECK(diary_vector.vector_norm(embedding)>0),
 FOREIGN KEY(owner_id,entry_id,profile_id,source_revision) REFERENCES diary_ai.entry_index(owner_id,entry_id,profile_id,source_revision),
 FOREIGN KEY(owner_id,profile_id,dimensions) REFERENCES diary_ai.embedding_profile(owner_id,id,dimensions));
CREATE TABLE diary_ai.job(
 owner_id bigint NOT NULL,id uuid NOT NULL,request_id uuid NOT NULL,request_hash text NOT NULL,
 kind text NOT NULL CHECK(kind IN('QUESTION','REVIEW','INDEX_START','INDEX_PAUSE')),
 state text NOT NULL CHECK(state IN('QUEUED','RUNNING','SUCCEEDED','FAILED','UNCERTAIN','STALE','CANCELED')),
 payload jsonb NOT NULL,scope_digest text,sources jsonb,partial boolean NOT NULL DEFAULT false,
 error_code text,possibly_charged boolean NOT NULL DEFAULT false,created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),lease_token uuid,
 PRIMARY KEY(owner_id,id),UNIQUE(owner_id,request_id));
CREATE UNIQUE INDEX job_one_running ON diary_ai.job((1)) WHERE state='RUNNING';
CREATE INDEX job_queue ON diary_ai.job(owner_id,state,created_at,id);
CREATE TABLE diary_ai.worker_slot(
 slot integer PRIMARY KEY CHECK(slot=1),owner_id bigint NOT NULL CHECK(owner_id>0),token uuid,expires_at timestamptz NOT NULL DEFAULT '-infinity');
CREATE TABLE diary_ai.report(
 owner_id bigint NOT NULL,id uuid NOT NULL,job_id uuid NOT NULL,kind text NOT NULL CHECK(kind IN('QUESTION','REVIEW')),
 state text NOT NULL CHECK(state IN('SUCCEEDED','STALE')),result jsonb,scope_description text NOT NULL,
 model text NOT NULL,prompt_version text NOT NULL,partial boolean NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(owner_id,id),UNIQUE(owner_id,job_id),FOREIGN KEY(owner_id,job_id) REFERENCES diary_ai.job(owner_id,id));
CREATE TABLE diary_ai.report_source(
 owner_id bigint NOT NULL,report_id uuid NOT NULL,source_id text NOT NULL,entry_id uuid NOT NULL,source_revision bigint NOT NULL,
 start_cp integer NOT NULL CHECK(start_cp>=0),end_cp integer NOT NULL CHECK(end_cp>=start_cp),text_hash text NOT NULL,
 PRIMARY KEY(owner_id,report_id,source_id),FOREIGN KEY(owner_id,report_id) REFERENCES diary_ai.report(owner_id,id) ON DELETE CASCADE,
 FOREIGN KEY(entry_id,owner_id) REFERENCES diary.entry(id,owner_id));
CREATE TABLE diary_ai.usage_budget(
 owner_id bigint NOT NULL CHECK(owner_id>0),day date NOT NULL,generations integer NOT NULL DEFAULT 0 CHECK(generations>=0),
 embedding_blocks integer NOT NULL DEFAULT 0 CHECK(embedding_blocks>=0),input_tokens bigint,output_tokens bigint,
 PRIMARY KEY(owner_id,day));
DO $$ DECLARE tbl text; BEGIN
 FOREACH tbl IN ARRAY ARRAY['embedding_profile','owner_settings','entry_index','chunk','job','worker_slot','report','report_source','usage_budget'] LOOP
  EXECUTE format('ALTER TABLE diary_ai.%I ENABLE ROW LEVEL SECURITY',tbl);
  EXECUTE format('ALTER TABLE diary_ai.%I FORCE ROW LEVEL SECURITY',tbl);
  EXECUTE format('CREATE POLICY diary_ai_owner ON diary_ai.%I TO diary_runtime USING(owner_id=nullif(current_setting(''diary.owner_id'',true),'''')::bigint) WITH CHECK(owner_id=nullif(current_setting(''diary.owner_id'',true),'''')::bigint)',tbl);
  EXECUTE format('REVOKE ALL ON diary_ai.%I FROM PUBLIC,diary_runtime',tbl);
  EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON diary_ai.%I TO diary_runtime',tbl);
 END LOOP;
END $$;
GRANT SELECT ON diary_ai.schema_version TO diary_runtime;
-- Receipt includes columns, constraints, indices, policies, owners, ACL and forced RLS.
INSERT INTO diary_ai.schema_version(version,fingerprint)
SELECT 1,md5(string_agg(def,E'\n' ORDER BY def)) FROM (
 SELECT 'column:'||c.relname||':'||a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull||':'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') AS def
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
 LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE n.nspname='diary_ai' AND c.relkind='r'
 UNION ALL SELECT 'constraint:'||c.relname||':'||pg_get_constraintdef(k.oid) FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='diary_ai'
 UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='diary_ai'
 UNION ALL SELECT 'policy:'||tablename||':'||policyname||':'||roles::text||':'||cmd||':'||qual||':'||with_check FROM pg_policies WHERE schemaname='diary_ai'
 UNION ALL SELECT 'table:'||c.relname||':'||c.relrowsecurity||':'||c.relforcerowsecurity||':'||r.rolname||':'||coalesce(c.relacl::text,'') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='diary_ai' AND c.relkind='r'
) shape;
\endif
COMMIT;
