\set ON_ERROR_STOP on
BEGIN;
SET LOCAL ROLE knowledge_migrator;
SET LOCAL statement_timeout='30s';
SELECT pg_advisory_xact_lock(1936876915,5);
CREATE SCHEMA IF NOT EXISTS knowledge_order AUTHORIZATION knowledge_migrator;
REVOKE ALL ON SCHEMA knowledge_order FROM PUBLIC;
GRANT USAGE ON SCHEMA knowledge_order TO knowledge_runtime;
CREATE TABLE IF NOT EXISTS knowledge_order.tag_order (
 owner_id bigint NOT NULL,
 tag_id uuid NOT NULL,
 positions jsonb NOT NULL CHECK(jsonb_typeof(positions)='object'),
 revision bigint NOT NULL CHECK(revision>0),
 PRIMARY KEY(owner_id,tag_id),
 FOREIGN KEY(owner_id,tag_id) REFERENCES knowledge.label(owner_id,id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS knowledge_order.mutation (
 owner_id bigint NOT NULL,
 id uuid NOT NULL,
 request_hash text NOT NULL,
 result jsonb NOT NULL,
 PRIMARY KEY(owner_id,id)
);
ALTER TABLE knowledge_order.tag_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_order.tag_order FORCE ROW LEVEL SECURITY;
ALTER TABLE knowledge_order.mutation ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_order.mutation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS owner_access ON knowledge_order.tag_order;
CREATE POLICY owner_access ON knowledge_order.tag_order USING(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint) WITH CHECK(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint);
DROP POLICY IF EXISTS owner_access ON knowledge_order.mutation;
CREATE POLICY owner_access ON knowledge_order.mutation USING(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint) WITH CHECK(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint);
REVOKE ALL ON ALL TABLES IN SCHEMA knowledge_order FROM PUBLIC;
GRANT SELECT,INSERT,UPDATE ON knowledge_order.tag_order TO knowledge_runtime;
GRANT SELECT,INSERT ON knowledge_order.mutation TO knowledge_runtime;
DO $$ BEGIN
 IF (SELECT count(*) FROM information_schema.columns WHERE table_schema='knowledge_order' AND table_name='tag_order')<>4
 OR (SELECT count(*) FROM information_schema.columns WHERE table_schema='knowledge_order' AND table_name='mutation')<>4
 OR (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='knowledge_order' AND c.relname IN('tag_order','mutation') AND c.relrowsecurity AND c.relforcerowsecurity AND r.rolname='knowledge_migrator')<>2 THEN
 RAISE EXCEPTION 'Knowledge order schema drift';
 END IF;
END $$;
COMMIT;
