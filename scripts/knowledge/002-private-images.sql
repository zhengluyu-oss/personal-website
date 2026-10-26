\set ON_ERROR_STOP on
BEGIN;
SET LOCAL ROLE knowledge_migrator;
SET LOCAL statement_timeout='30s';
SELECT pg_advisory_xact_lock(1936876915,2);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM knowledge.schema_version WHERE version=1) THEN
    RAISE EXCEPTION 'Knowledge v1 schema is required';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM knowledge.schema_version WHERE version=2)
     AND to_regclass('knowledge.image') IS NOT NULL THEN
    RAISE EXCEPTION 'Unversioned knowledge image table';
  END IF;
END $$;
SELECT EXISTS(SELECT 1 FROM knowledge.schema_version WHERE version=2) AS image_schema_exists \gset
\if :image_schema_exists
\else
CREATE TABLE knowledge.image (
  owner_id bigint NOT NULL,
  id uuid NOT NULL,
  entry_id uuid NOT NULL,
  mutation_id uuid NOT NULL,
  request_hash text NOT NULL CHECK (length(request_hash)=64),
  object_key text NOT NULL,
  mime text NOT NULL CHECK (mime IN ('image/jpeg','image/png','image/webp')),
  size_bytes bigint NOT NULL CHECK (size_bytes>0),
  width integer NOT NULL CHECK (width BETWEEN 1 AND 12000),
  height integer NOT NULL CHECK (height BETWEEN 1 AND 12000),
  state text NOT NULL CHECK (state IN ('ACTIVE','CLEANUP_PENDING','CLEANED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(owner_id,id),
  UNIQUE(owner_id,mutation_id),
  UNIQUE(owner_id,object_key),
  FOREIGN KEY(owner_id,entry_id) REFERENCES knowledge.entry(owner_id,id)
);
CREATE INDEX image_entry ON knowledge.image(owner_id,entry_id,created_at);
CREATE INDEX image_cleanup ON knowledge.image(owner_id,state,created_at);
ALTER TABLE knowledge.image ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge.image FORCE ROW LEVEL SECURITY;
CREATE POLICY knowledge_owner ON knowledge.image TO knowledge_runtime
  USING(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint)
  WITH CHECK(owner_id=nullif(current_setting('knowledge.owner_id',true),'')::bigint);
REVOKE ALL ON knowledge.image FROM PUBLIC,knowledge_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON knowledge.image TO knowledge_runtime;
\endif
DO $$ DECLARE actual text; BEGIN
WITH shape AS (
  SELECT 'column:'||a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull||':'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') AS def
    FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
   WHERE a.attrelid='knowledge.image'::regclass AND a.attnum>0 AND NOT a.attisdropped
  UNION ALL SELECT 'constraint:'||pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='knowledge.image'::regclass
  UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='knowledge' AND tablename='image'
  UNION ALL SELECT 'policy:'||roles::text||':'||cmd||':'||qual||':'||with_check FROM pg_policies WHERE schemaname='knowledge' AND tablename='image'
  UNION ALL SELECT 'table:'||c.relrowsecurity||':'||c.relforcerowsecurity||':'||r.rolname||':'||coalesce(c.relacl::text,'')
    FROM pg_class c JOIN pg_roles r ON r.oid=c.relowner WHERE c.oid='knowledge.image'::regclass
), digest AS (SELECT md5(string_agg(def,E'\n' ORDER BY def)) AS value FROM shape)
SELECT value INTO actual FROM digest;
INSERT INTO knowledge.schema_version(version,fingerprint)
SELECT 2,actual ON CONFLICT(version) DO NOTHING;
  IF NOT EXISTS (SELECT 1 FROM knowledge.schema_version WHERE version=2)
    OR (SELECT fingerprint FROM knowledge.schema_version WHERE version=2)
       IS DISTINCT FROM actual
    OR NOT (SELECT c.relrowsecurity AND c.relforcerowsecurity AND r.rolname='knowledge_migrator'
              FROM pg_class c JOIN pg_roles r ON r.oid=c.relowner WHERE c.oid='knowledge.image'::regclass)
    OR NOT has_table_privilege('knowledge_runtime','knowledge.image','SELECT,INSERT,UPDATE,DELETE') THEN
    RAISE EXCEPTION 'Knowledge image schema drift or missing permissions';
  END IF;
END $$;
COMMIT;
