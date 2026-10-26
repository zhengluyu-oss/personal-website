\set ON_ERROR_STOP on
BEGIN;
SET LOCAL ROLE knowledge_migrator;
SET LOCAL statement_timeout='30s';
SELECT pg_advisory_xact_lock(1936876915,4);
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM knowledge.schema_version WHERE version=3) THEN RAISE EXCEPTION 'Knowledge v3 required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM knowledge.schema_version WHERE version=4)
    AND to_regclass('knowledge_assistant.usage') IS NOT NULL THEN RAISE EXCEPTION 'Unversioned assistant usage table'; END IF;
END $$;
CREATE SCHEMA IF NOT EXISTS knowledge_assistant AUTHORIZATION knowledge_migrator;
REVOKE ALL ON SCHEMA knowledge_assistant FROM PUBLIC;
GRANT USAGE ON SCHEMA knowledge_assistant TO knowledge_runtime;
CREATE TABLE IF NOT EXISTS knowledge_assistant.usage (
 owner_id bigint NOT NULL,
 id uuid NOT NULL,
 account_id bigint NOT NULL CHECK(account_id>0),
 usage_day date NOT NULL,
 state text NOT NULL CHECK(state IN('IN_FLIGHT','COMPLETED','UNCERTAIN','REJECTED')),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(owner_id,id)
);
CREATE INDEX IF NOT EXISTS assistant_usage_day ON knowledge_assistant.usage(owner_id,usage_day,account_id,state);
ALTER TABLE knowledge_assistant.usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_assistant.usage FORCE ROW LEVEL SECURITY;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='knowledge_assistant' AND tablename='usage' AND policyname='knowledge_owner') THEN
  CREATE POLICY knowledge_owner ON knowledge_assistant.usage TO knowledge_runtime
    USING(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint)
    WITH CHECK(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint);
 END IF;
END $$;
REVOKE ALL ON knowledge_assistant.usage FROM PUBLIC,knowledge_runtime;
GRANT SELECT,INSERT,UPDATE ON knowledge_assistant.usage TO knowledge_runtime;
DO $$ DECLARE actual text; BEGIN
 SELECT md5(string_agg(def,E'\n' ORDER BY def)) INTO actual FROM (
  SELECT 'column:'||a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull||':'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') AS def
    FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
   WHERE a.attrelid='knowledge_assistant.usage'::regclass AND a.attnum>0 AND NOT a.attisdropped
  UNION ALL SELECT 'constraint:'||pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='knowledge_assistant.usage'::regclass
  UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='knowledge_assistant' AND tablename='usage'
  UNION ALL SELECT 'policy:'||policyname||':'||roles::text||':'||cmd||':'||qual||':'||with_check FROM pg_policies WHERE schemaname='knowledge_assistant' AND tablename='usage'
  UNION ALL SELECT 'table:'||c.relrowsecurity||':'||c.relforcerowsecurity||':'||r.rolname||':'||coalesce(c.relacl::text,'')
    FROM pg_class c JOIN pg_roles r ON r.oid=c.relowner WHERE c.oid='knowledge_assistant.usage'::regclass
 ) shape;
 INSERT INTO knowledge.schema_version(version,fingerprint) VALUES(4,actual) ON CONFLICT(version) DO NOTHING;
 IF (SELECT fingerprint FROM knowledge.schema_version WHERE version=4) IS DISTINCT FROM actual THEN RAISE EXCEPTION 'Assistant usage schema drift'; END IF;
END $$;
COMMIT;
