-- Dedicated private schema avoids changing the existing AI/coaching schema receipts.
BEGIN;
SET LOCAL ROLE diary_migrator;
SET LOCAL statement_timeout='30s';
SELECT pg_advisory_xact_lock(1936876914,4);
CREATE SCHEMA IF NOT EXISTS diary_diagnostics AUTHORIZATION diary_migrator;
REVOKE ALL ON SCHEMA diary_diagnostics FROM PUBLIC;
GRANT USAGE ON SCHEMA diary_diagnostics TO diary_runtime;
CREATE TABLE IF NOT EXISTS diary_diagnostics.failure(
 owner_id bigint NOT NULL CHECK(owner_id>0),job_id uuid NOT NULL,
 kind text NOT NULL CHECK(kind IN('QUESTION','REVIEW','PERIOD')),
 reason text NOT NULL,field_path text NOT NULL,model text NOT NULL,prompt_version text NOT NULL,
 raw text,sources text,retention text NOT NULL CHECK(retention IN('AVAILABLE','NOT_RETAINED','EXPIRED','DELETED','STALE')),
 created_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL DEFAULT now()+interval '7 days',
 PRIMARY KEY(owner_id,job_id,kind),CHECK(expires_at=created_at+interval '7 days'),
 CHECK(octet_length(raw)<=131072),CHECK(octet_length(sources)<=524288),
 CHECK((retention='AVAILABLE' AND raw IS NOT NULL AND sources IS NOT NULL) OR (retention<>'AVAILABLE' AND raw IS NULL AND sources IS NULL)));
ALTER TABLE diary_diagnostics.failure ENABLE ROW LEVEL SECURITY;
ALTER TABLE diary_diagnostics.failure FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS diary_diagnostics_owner ON diary_diagnostics.failure;
CREATE POLICY diary_diagnostics_owner ON diary_diagnostics.failure TO diary_runtime
 USING(owner_id=nullif(current_setting('diary.owner_id',true),'')::bigint)
 WITH CHECK(owner_id=nullif(current_setting('diary.owner_id',true),'')::bigint);
REVOKE ALL ON diary_diagnostics.failure FROM PUBLIC,diary_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON diary_diagnostics.failure TO diary_runtime;
CREATE INDEX IF NOT EXISTS failure_expiry ON diary_diagnostics.failure(owner_id,expires_at) WHERE retention='AVAILABLE';
COMMIT;
