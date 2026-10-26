-- Same PostgreSQL database, separate schema: diary_ai's nine-table receipt must stay unchanged.
-- Apply with psql -X -v ON_ERROR_STOP=1 after 002-private-diary-ai.sql.
BEGIN;
SET LOCAL ROLE diary_migrator;
SET LOCAL statement_timeout='30s';
SELECT pg_advisory_xact_lock(1936876914,5);

DO $$ BEGIN
 IF to_regclass('diary_ai.job') IS NULL OR to_regclass('diary_ai.report') IS NULL THEN
  RAISE EXCEPTION 'Install the private diary AI schema first';
 END IF;
 IF to_regclass('diary_conversation.conversation') IS NOT NULL
    AND to_regclass('diary_conversation.schema_version') IS NULL THEN
  RAISE EXCEPTION 'Unversioned diary conversation schema';
 END IF;
END $$;

CREATE SCHEMA IF NOT EXISTS diary_conversation AUTHORIZATION diary_migrator;
REVOKE ALL ON SCHEMA diary_conversation FROM PUBLIC;
GRANT USAGE ON SCHEMA diary_conversation TO diary_runtime;

CREATE TABLE IF NOT EXISTS diary_conversation.schema_version(
 version integer PRIMARY KEY CHECK(version=1),
 installed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS diary_conversation.conversation(
 owner_id bigint NOT NULL CHECK(owner_id>0),
 id uuid NOT NULL,
 kind text NOT NULL CHECK(kind IN('QUESTION','REVIEW')),
 scope jsonb,
 anchor_digest text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(owner_id,id));
CREATE TABLE IF NOT EXISTS diary_conversation.turn(
 owner_id bigint NOT NULL CHECK(owner_id>0),
 id uuid NOT NULL,
 conversation_id uuid NOT NULL,
 sequence_no integer NOT NULL CHECK(sequence_no>0),
 request_id uuid NOT NULL,
 job_id uuid NOT NULL,
 user_text text,
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(owner_id,id),
 UNIQUE(owner_id,conversation_id,sequence_no),
 UNIQUE(owner_id,request_id),
 UNIQUE(owner_id,job_id),
 FOREIGN KEY(owner_id,conversation_id) REFERENCES diary_conversation.conversation(owner_id,id) ON DELETE CASCADE,
 FOREIGN KEY(owner_id,job_id) REFERENCES diary_ai.job(owner_id,id));
CREATE INDEX IF NOT EXISTS conversation_recent
 ON diary_conversation.conversation(owner_id,updated_at DESC,id DESC);
CREATE INDEX IF NOT EXISTS turn_order
 ON diary_conversation.turn(owner_id,conversation_id,sequence_no);

ALTER TABLE diary_conversation.conversation ENABLE ROW LEVEL SECURITY;
ALTER TABLE diary_conversation.conversation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS diary_conversation_owner ON diary_conversation.conversation;
CREATE POLICY diary_conversation_owner ON diary_conversation.conversation TO diary_runtime
 USING(owner_id=nullif(current_setting('diary.owner_id',true),'')::bigint)
 WITH CHECK(owner_id=nullif(current_setting('diary.owner_id',true),'')::bigint);
ALTER TABLE diary_conversation.turn ENABLE ROW LEVEL SECURITY;
ALTER TABLE diary_conversation.turn FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS diary_conversation_owner ON diary_conversation.turn;
CREATE POLICY diary_conversation_owner ON diary_conversation.turn TO diary_runtime
 USING(owner_id=nullif(current_setting('diary.owner_id',true),'')::bigint)
 WITH CHECK(owner_id=nullif(current_setting('diary.owner_id',true),'')::bigint);

REVOKE ALL ON diary_conversation.schema_version,diary_conversation.conversation,diary_conversation.turn FROM PUBLIC,diary_runtime;
GRANT SELECT ON diary_conversation.schema_version TO diary_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON diary_conversation.conversation,diary_conversation.turn TO diary_runtime;
INSERT INTO diary_conversation.schema_version(version) VALUES(1) ON CONFLICT DO NOTHING;
COMMIT;
