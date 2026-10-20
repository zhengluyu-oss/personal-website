BEGIN;
SET LOCAL ROLE knowledge_migrator;
SET LOCAL statement_timeout='30s';
SELECT pg_advisory_xact_lock(1936876915,1);
SELECT to_regnamespace('knowledge') IS NOT NULL AS knowledge_exists \gset
\if :knowledge_exists
DO $$ DECLARE expected text; actual text; BEGIN
 IF to_regclass('knowledge.schema_version') IS NULL THEN RAISE EXCEPTION 'Unversioned knowledge schema'; END IF;
 SELECT fingerprint INTO expected FROM knowledge.schema_version WHERE version=1;
 SELECT md5(string_agg(def,E'\n' ORDER BY def)) INTO actual FROM (
 SELECT 'column:'||c.relname||':'||a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull||':'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') AS def
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
 LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE n.nspname='knowledge' AND c.relkind='r'
 UNION ALL SELECT 'constraint:'||c.relname||':'||pg_get_constraintdef(k.oid) FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='knowledge'
 UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='knowledge'
 UNION ALL SELECT 'policy:'||tablename||':'||policyname||':'||roles::text||':'||cmd||':'||qual||':'||with_check FROM pg_policies WHERE schemaname='knowledge'
 UNION ALL SELECT 'table:'||c.relname||':'||c.relrowsecurity||':'||c.relforcerowsecurity||':'||r.rolname||':'||coalesce(c.relacl::text,'') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='knowledge' AND c.relkind='r'
 ) shape;
 IF expected IS NULL OR expected<>actual THEN RAISE EXCEPTION 'Knowledge schema drift'; END IF;
END $$;
\else
CREATE SCHEMA knowledge AUTHORIZATION knowledge_migrator;
REVOKE ALL ON SCHEMA knowledge FROM PUBLIC;
GRANT USAGE ON SCHEMA knowledge TO knowledge_runtime;
CREATE TABLE knowledge.schema_version(version integer PRIMARY KEY,fingerprint text NOT NULL);
CREATE TABLE knowledge.owner_control(owner_id bigint PRIMARY KEY CHECK(owner_id>0));
CREATE TABLE knowledge.label(owner_id bigint NOT NULL,id uuid NOT NULL,kind text NOT NULL CHECK(kind IN('CATEGORY','TAG')),name text NOT NULL CHECK(length(name) BETWEEN 1 AND 80),revision bigint NOT NULL CHECK(revision>0),PRIMARY KEY(owner_id,id),UNIQUE(owner_id,kind,name));
CREATE TABLE knowledge.entry(
 owner_id bigint NOT NULL,id uuid NOT NULL,kind text NOT NULL CHECK(kind IN('NOTE','DOCUMENT','CONCEPT')),
 title text NOT NULL CHECK(length(title) BETWEEN 1 AND 200),body text NOT NULL CHECK(octet_length(body)<=2097152),source_url text NOT NULL DEFAULT '' CHECK(length(source_url)<=2000),category_id uuid,
 revision bigint NOT NULL CHECK(revision>0),current_file_version uuid,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),deleted_at timestamptz,
 PRIMARY KEY(owner_id,id),FOREIGN KEY(owner_id,category_id) REFERENCES knowledge.label(owner_id,id));
CREATE INDEX entry_listing ON knowledge.entry(owner_id,deleted_at,updated_at DESC,id);
CREATE TABLE knowledge.entry_tag(owner_id bigint NOT NULL,entry_id uuid NOT NULL,tag_id uuid NOT NULL,PRIMARY KEY(owner_id,entry_id,tag_id),FOREIGN KEY(owner_id,entry_id) REFERENCES knowledge.entry(owner_id,id),FOREIGN KEY(owner_id,tag_id) REFERENCES knowledge.label(owner_id,id));
CREATE TABLE knowledge.entry_revision(owner_id bigint NOT NULL,entry_id uuid NOT NULL,revision bigint NOT NULL,content jsonb NOT NULL,current_file_version uuid,saved_at timestamptz NOT NULL,deleted_at timestamptz,action text NOT NULL,PRIMARY KEY(owner_id,entry_id,revision),FOREIGN KEY(owner_id,entry_id) REFERENCES knowledge.entry(owner_id,id));
CREATE TABLE knowledge.mutation(owner_id bigint NOT NULL,id uuid NOT NULL,request_hash text NOT NULL CHECK(length(request_hash)=64),result jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(owner_id,id));
CREATE TABLE knowledge.file_version(owner_id bigint NOT NULL,id uuid NOT NULL,entry_id uuid NOT NULL,entry_revision bigint NOT NULL,object_key text NOT NULL,filename text NOT NULL CHECK(length(filename)<=200),mime text NOT NULL,sha256 text NOT NULL CHECK(length(sha256)=64),size_bytes bigint NOT NULL CHECK(size_bytes>0),state text NOT NULL CHECK(state IN('TEMPORARY','CLAIMED','CLEANUP_PENDING','CLEANED')),created_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(owner_id,id),UNIQUE(owner_id,object_key),FOREIGN KEY(owner_id,entry_id) REFERENCES knowledge.entry(owner_id,id));
CREATE INDEX file_entry ON knowledge.file_version(owner_id,entry_id,id);
ALTER TABLE knowledge.entry ADD FOREIGN KEY(owner_id,current_file_version) REFERENCES knowledge.file_version(owner_id,id);
CREATE TABLE knowledge.processing_job(owner_id bigint NOT NULL,id uuid NOT NULL,entry_id uuid NOT NULL,file_version uuid NOT NULL,state text NOT NULL CHECK(state IN('QUEUED','RUNNING','READY','FAILED','NEEDS_OCR','CANCELED','RETRY_WAIT')),attempt integer NOT NULL DEFAULT 0 CHECK(attempt BETWEEN 0 AND 3),fence uuid,lease_until timestamptz,retry_after timestamptz,error_code text,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(owner_id,id),FOREIGN KEY(owner_id,entry_id) REFERENCES knowledge.entry(owner_id,id),FOREIGN KEY(owner_id,file_version) REFERENCES knowledge.file_version(owner_id,id));
CREATE INDEX processing_queue ON knowledge.processing_job(owner_id,state,retry_after,created_at);
CREATE UNIQUE INDEX processing_one_running ON knowledge.processing_job((1)) WHERE state='RUNNING';
CREATE TABLE knowledge.outbox(owner_id bigint NOT NULL,id uuid NOT NULL,job_id uuid NOT NULL,available_at timestamptz NOT NULL DEFAULT now(),confirmed_at timestamptz,PRIMARY KEY(owner_id,id),FOREIGN KEY(owner_id,job_id) REFERENCES knowledge.processing_job(owner_id,id));
CREATE INDEX pending_outbox ON knowledge.outbox(owner_id,available_at) WHERE confirmed_at IS NULL;
CREATE TABLE knowledge.extracted_segment(owner_id bigint NOT NULL,entry_id uuid NOT NULL,file_version uuid NOT NULL,ordinal integer NOT NULL CHECK(ordinal>=0),page integer,from_line integer,to_line integer,body text NOT NULL CHECK(octet_length(body)<=2097152),PRIMARY KEY(owner_id,file_version,ordinal),FOREIGN KEY(owner_id,entry_id) REFERENCES knowledge.entry(owner_id,id),FOREIGN KEY(owner_id,file_version) REFERENCES knowledge.file_version(owner_id,id));
CREATE TABLE knowledge.relation(owner_id bigint NOT NULL,id uuid NOT NULL,from_kind text NOT NULL CHECK(from_kind IN('ENTRY','ARTICLE')),from_id text NOT NULL,to_kind text NOT NULL CHECK(to_kind IN('ENTRY','ARTICLE')),to_id text NOT NULL,type text NOT NULL CHECK(type IN('EXPLAINS','DEPENDS_ON','REFERENCES','RELATED_TO')),note text NOT NULL CHECK(length(note)<=1000),revision bigint NOT NULL CHECK(revision>0),deleted_at timestamptz,PRIMARY KEY(owner_id,id),CHECK(from_kind<>to_kind OR from_id<>to_id));
CREATE UNIQUE INDEX relation_unique ON knowledge.relation(owner_id,from_kind,from_id,to_kind,to_id,type) WHERE deleted_at IS NULL;
CREATE INDEX relation_from ON knowledge.relation(owner_id,from_kind,from_id) WHERE deleted_at IS NULL;
CREATE INDEX relation_to ON knowledge.relation(owner_id,to_kind,to_id) WHERE deleted_at IS NULL;
CREATE TABLE knowledge.article_export(owner_id bigint NOT NULL,id uuid NOT NULL,request_hash text NOT NULL,snapshot jsonb NOT NULL,state text NOT NULL CHECK(state IN('PREPARED','CREATED','RETRYABLE','REJECTED','DELETED')),article_id bigint,error_code text,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(owner_id,id));
DO $$ DECLARE tbl text; BEGIN
 FOREACH tbl IN ARRAY ARRAY['owner_control','label','entry','entry_tag','entry_revision','mutation','file_version','processing_job','outbox','extracted_segment','relation','article_export'] LOOP
  EXECUTE format('ALTER TABLE knowledge.%I ENABLE ROW LEVEL SECURITY',tbl);
  EXECUTE format('ALTER TABLE knowledge.%I FORCE ROW LEVEL SECURITY',tbl);
  EXECUTE format('CREATE POLICY knowledge_owner ON knowledge.%I TO knowledge_runtime USING(owner_id=nullif(current_setting(''knowledge.owner_id'',true),'''')::bigint) WITH CHECK(owner_id=nullif(current_setting(''knowledge.owner_id'',true),'''')::bigint)',tbl);
  EXECUTE format('REVOKE ALL ON knowledge.%I FROM PUBLIC,knowledge_runtime',tbl);
  IF tbl IN('entry_revision','mutation') THEN EXECUTE format('GRANT SELECT,INSERT ON knowledge.%I TO knowledge_runtime',tbl);
  ELSE EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON knowledge.%I TO knowledge_runtime',tbl); END IF;
 END LOOP;
END $$;
REVOKE ALL ON knowledge.schema_version FROM PUBLIC,knowledge_runtime;
GRANT SELECT ON knowledge.schema_version TO knowledge_runtime;
INSERT INTO knowledge.schema_version(version,fingerprint)
SELECT 1,md5(string_agg(def,E'\n' ORDER BY def)) FROM (
 SELECT 'column:'||c.relname||':'||a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull||':'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') AS def
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
 LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE n.nspname='knowledge' AND c.relkind='r'
 UNION ALL SELECT 'constraint:'||c.relname||':'||pg_get_constraintdef(k.oid) FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='knowledge'
 UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='knowledge'
 UNION ALL SELECT 'policy:'||tablename||':'||policyname||':'||roles::text||':'||cmd||':'||qual||':'||with_check FROM pg_policies WHERE schemaname='knowledge'
 UNION ALL SELECT 'table:'||c.relname||':'||c.relrowsecurity||':'||c.relforcerowsecurity||':'||r.rolname||':'||coalesce(c.relacl::text,'') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='knowledge' AND c.relkind='r'
) shape;
\endif
COMMIT;
