-- Independent manual coaching storage; works with 001 alone. No extension or AI required.
BEGIN;
SET LOCAL ROLE diary_migrator;
SELECT pg_advisory_xact_lock(1936876914,3);
SET LOCAL statement_timeout='30s';
SELECT to_regnamespace('diary_coaching') IS NOT NULL AS coaching_exists \gset
\if :coaching_exists
DO $$ DECLARE actual text; expected text; BEGIN
 IF to_regclass('diary_coaching.schema_version') IS NULL THEN RAISE EXCEPTION 'Unversioned coaching schema'; END IF;
 SELECT fingerprint INTO expected FROM diary_coaching.schema_version WHERE version=1;
 SELECT md5(string_agg(def,E'\n' ORDER BY def)) INTO actual FROM (
 SELECT 'column:'||c.relname||':'||a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull||':'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') AS def
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
 LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE n.nspname='diary_coaching' AND c.relkind='r'
 UNION ALL SELECT 'constraint:'||c.relname||':'||pg_get_constraintdef(k.oid) FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='diary_coaching'
 UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='diary_coaching'
 UNION ALL SELECT 'policy:'||tablename||':'||policyname||':'||roles::text||':'||cmd||':'||qual||':'||with_check FROM pg_policies WHERE schemaname='diary_coaching'
 UNION ALL SELECT 'table:'||c.relname||':'||c.relrowsecurity||':'||c.relforcerowsecurity||':'||r.rolname||':'||coalesce(c.relacl::text,'') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='diary_coaching' AND c.relkind='r'

 ) shape;
 IF expected IS NULL OR expected<>actual THEN RAISE EXCEPTION 'Coaching schema drift; migration refused'; END IF;
END $$;
\else
CREATE SCHEMA diary_coaching AUTHORIZATION diary_migrator;
REVOKE ALL ON SCHEMA diary_coaching FROM PUBLIC;
GRANT USAGE ON SCHEMA diary_coaching TO diary_runtime;
CREATE TABLE diary_coaching.schema_version(version integer PRIMARY KEY CHECK(version=1),fingerprint text NOT NULL,installed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE diary_coaching.owner_control(owner_id bigint PRIMARY KEY CHECK(owner_id>0));
CREATE TABLE diary_coaching.goal(
 owner_id bigint NOT NULL CHECK(owner_id>0),id uuid NOT NULL,parent_id uuid,
 revision bigint NOT NULL CHECK(revision>0),data jsonb NOT NULL CHECK(jsonb_typeof(data)='object'),
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),deleted_at timestamptz,
 PRIMARY KEY(owner_id,id),CHECK(parent_id IS NULL),CHECK(length(data->>'title') BETWEEN 1 AND 120),CHECK(data->>'state' IN('ACTIVE','PAUSED','COMPLETED','ARCHIVED')));
CREATE INDEX goal_parent ON diary_coaching.goal(owner_id,parent_id,updated_at,id);
CREATE TABLE diary_coaching.goal_revision(
 owner_id bigint NOT NULL,id uuid NOT NULL,revision bigint NOT NULL CHECK(revision>0),data jsonb NOT NULL,
 saved_at timestamptz NOT NULL,deleted_at timestamptz,PRIMARY KEY(owner_id,id,revision),
 FOREIGN KEY(owner_id,id) REFERENCES diary_coaching.goal(owner_id,id));
CREATE TABLE diary_coaching.action(
 owner_id bigint NOT NULL CHECK(owner_id>0),id uuid NOT NULL,parent_id uuid,
 revision bigint NOT NULL CHECK(revision>0),data jsonb NOT NULL CHECK(jsonb_typeof(data)='object'),
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),deleted_at timestamptz,
 PRIMARY KEY(owner_id,id),FOREIGN KEY(owner_id,parent_id) REFERENCES diary_coaching.goal(owner_id,id),CHECK(parent_id IS NOT NULL),CHECK(length(data->>'text') BETWEEN 1 AND 2000),CHECK(data->>'state' IN('PLANNED','IN_PROGRESS','COMPLETED','SKIPPED','ARCHIVED')));
CREATE INDEX action_parent ON diary_coaching.action(owner_id,parent_id,updated_at,id);
CREATE TABLE diary_coaching.action_revision(
 owner_id bigint NOT NULL,id uuid NOT NULL,revision bigint NOT NULL CHECK(revision>0),data jsonb NOT NULL,
 saved_at timestamptz NOT NULL,deleted_at timestamptz,PRIMARY KEY(owner_id,id,revision),
 FOREIGN KEY(owner_id,id) REFERENCES diary_coaching.action(owner_id,id));
CREATE TABLE diary_coaching.feedback(
 owner_id bigint NOT NULL CHECK(owner_id>0),id uuid NOT NULL,parent_id uuid,
 revision bigint NOT NULL CHECK(revision>0),data jsonb NOT NULL CHECK(jsonb_typeof(data)='object'),
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),deleted_at timestamptz,
 PRIMARY KEY(owner_id,id),FOREIGN KEY(owner_id,parent_id) REFERENCES diary_coaching.action(owner_id,id),CHECK(parent_id IS NOT NULL),CHECK(data->>'execution' IN('TRIED','COMPLETED','SKIPPED')),CHECK(data->>'effect' IN('HELPFUL','NOT_HELPFUL','UNSURE','NOT_EVALUATED')));
CREATE INDEX feedback_parent ON diary_coaching.feedback(owner_id,parent_id,updated_at,id);
CREATE TABLE diary_coaching.feedback_revision(
 owner_id bigint NOT NULL,id uuid NOT NULL,revision bigint NOT NULL CHECK(revision>0),data jsonb NOT NULL,
 saved_at timestamptz NOT NULL,deleted_at timestamptz,PRIMARY KEY(owner_id,id,revision),
 FOREIGN KEY(owner_id,id) REFERENCES diary_coaching.feedback(owner_id,id));
CREATE TABLE diary_coaching.mutation(
 owner_id bigint NOT NULL,mutation_id uuid NOT NULL,request_hash text NOT NULL CHECK(request_hash~'^[0-9a-f]{64}$'),ack jsonb NOT NULL,
 PRIMARY KEY(owner_id,mutation_id));
CREATE TABLE diary_coaching.origin_link(
 owner_id bigint NOT NULL,action_id uuid NOT NULL,type text NOT NULL CHECK(type IN('AI_REPORT','PERIOD_REPORT')),
 report_id uuid NOT NULL,suggestion_index integer NOT NULL CHECK(suggestion_index BETWEEN 0 AND 19),suggestion_hash text NOT NULL,
 PRIMARY KEY(owner_id,action_id),FOREIGN KEY(owner_id,action_id) REFERENCES diary_coaching.action(owner_id,id));
-- External report IDs are non-authorizing links: source deletion must not delete human records.
CREATE TABLE diary_coaching.period_job(
 owner_id bigint NOT NULL,id uuid NOT NULL,request_id uuid NOT NULL,request_hash text NOT NULL,
 state text NOT NULL CHECK(state IN('QUEUED','RUNNING','SUCCEEDED','FAILED','UNCERTAIN','STALE','CANCELED')),
 payload jsonb,scope_digest text,sources jsonb,error_code text,possibly_charged boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),lease_token uuid,
 PRIMARY KEY(owner_id,id),UNIQUE(owner_id,request_id));
CREATE INDEX period_queue ON diary_coaching.period_job(owner_id,state,created_at,id);
CREATE UNIQUE INDEX period_one_running ON diary_coaching.period_job((1)) WHERE state='RUNNING';
CREATE TABLE diary_coaching.period_report(
 owner_id bigint NOT NULL,id uuid NOT NULL,job_id uuid NOT NULL,state text NOT NULL CHECK(state IN('SUCCEEDED','STALE')),
 result jsonb,scope jsonb,scope_digest text,model text NOT NULL,prompt_version text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(owner_id,id),UNIQUE(owner_id,job_id),FOREIGN KEY(owner_id,job_id) REFERENCES diary_coaching.period_job(owner_id,id));
CREATE TABLE diary_coaching.period_source(
 owner_id bigint NOT NULL,report_id uuid NOT NULL,source_id text NOT NULL,kind text NOT NULL CHECK(kind IN('D','G','A','F')),
 entity_id uuid NOT NULL,revision bigint NOT NULL CHECK(revision>0),field text NOT NULL,start_cp integer NOT NULL CHECK(start_cp>=0),
 end_cp integer NOT NULL CHECK(end_cp>=start_cp),text_hash text NOT NULL,
 PRIMARY KEY(owner_id,report_id,source_id),FOREIGN KEY(owner_id,report_id) REFERENCES diary_coaching.period_report(owner_id,id) ON DELETE CASCADE);
DO $$ DECLARE tbl text; BEGIN
 FOREACH tbl IN ARRAY ARRAY['owner_control','goal','action','feedback','goal_revision','action_revision','feedback_revision','mutation','origin_link','period_job','period_report','period_source'] LOOP
  EXECUTE format('ALTER TABLE diary_coaching.%I ENABLE ROW LEVEL SECURITY',tbl);
  EXECUTE format('ALTER TABLE diary_coaching.%I FORCE ROW LEVEL SECURITY',tbl);
  EXECUTE format('CREATE POLICY coaching_owner ON diary_coaching.%I TO diary_runtime USING(owner_id=nullif(current_setting(''diary.owner_id'',true),'''')::bigint) WITH CHECK(owner_id=nullif(current_setting(''diary.owner_id'',true),'''')::bigint)',tbl);
  EXECUTE format('REVOKE ALL ON diary_coaching.%I FROM PUBLIC,diary_runtime',tbl);
  IF tbl IN('goal_revision','action_revision','feedback_revision','mutation','origin_link') THEN
   EXECUTE format('GRANT SELECT,INSERT ON diary_coaching.%I TO diary_runtime',tbl);
  ELSIF tbl IN('goal','action','feedback','owner_control') THEN
   EXECUTE format('GRANT SELECT,INSERT,UPDATE ON diary_coaching.%I TO diary_runtime',tbl);
  ELSE
   EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON diary_coaching.%I TO diary_runtime',tbl);
  END IF;
 END LOOP;
END $$;
GRANT SELECT ON diary_coaching.schema_version TO diary_runtime;
INSERT INTO diary_coaching.schema_version(version,fingerprint)
SELECT 1,md5(string_agg(def,E'\n' ORDER BY def)) FROM (
 SELECT 'column:'||c.relname||':'||a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull||':'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') AS def
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
 LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE n.nspname='diary_coaching' AND c.relkind='r'
 UNION ALL SELECT 'constraint:'||c.relname||':'||pg_get_constraintdef(k.oid) FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='diary_coaching'
 UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='diary_coaching'
 UNION ALL SELECT 'policy:'||tablename||':'||policyname||':'||roles::text||':'||cmd||':'||qual||':'||with_check FROM pg_policies WHERE schemaname='diary_coaching'
 UNION ALL SELECT 'table:'||c.relname||':'||c.relrowsecurity||':'||c.relforcerowsecurity||':'||r.rolname||':'||coalesce(c.relacl::text,'') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='diary_coaching' AND c.relkind='r'

) shape;
\endif
COMMIT;
