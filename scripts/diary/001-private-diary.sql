-- PostgreSQL 16；数据库须预先创建并由 diary_migrator 拥有。
-- 由迁移账号执行：psql -v ON_ERROR_STOP=1 -f 001-private-diary.sql
-- 不由应用启动执行，不包含生产连接或秘密。
BEGIN;
SET LOCAL ROLE diary_migrator;
SELECT pg_advisory_xact_lock(1936876914, 1);
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $$
BEGIN
    IF to_regnamespace('diary') IS NOT NULL
       AND to_regclass('diary.schema_version') IS NULL THEN
        RAISE EXCEPTION 'Unversioned diary schema exists; migration refused';
    END IF;
END $$;
CREATE SCHEMA IF NOT EXISTS diary AUTHORIZATION diary_migrator;
REVOKE ALL ON SCHEMA diary FROM PUBLIC;
GRANT USAGE ON SCHEMA diary TO diary_runtime;
CREATE TABLE IF NOT EXISTS diary.schema_version (
    version integer PRIMARY KEY CHECK (version = 1),
    fingerprint text NOT NULL,
    installed_at timestamptz NOT NULL DEFAULT transaction_timestamp()
);
CREATE TABLE IF NOT EXISTS diary.entry (
    id uuid PRIMARY KEY,
    owner_id bigint NOT NULL CHECK (owner_id > 0),
    title text NOT NULL CHECK (char_length(title) <= 120),
    body text NOT NULL CHECK (char_length(body) <= 100000),
    covered_from date,
    covered_to date,
    display_timezone text NOT NULL DEFAULT 'Asia/Shanghai' CHECK (display_timezone = 'Asia/Shanghai'),
    revision bigint NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    deleted_at timestamptz,
    UNIQUE (id, owner_id),
    CHECK ((covered_from IS NULL AND covered_to IS NULL)
        OR (covered_from IS NOT NULL AND covered_to IS NOT NULL AND covered_from <= covered_to)),
    CHECK (updated_at >= created_at)
);
CREATE TABLE IF NOT EXISTS diary.entry_revision (
    entry_id uuid NOT NULL,
    owner_id bigint NOT NULL CHECK (owner_id > 0),
    revision bigint NOT NULL CHECK (revision > 0),
    title text NOT NULL CHECK (char_length(title) <= 120),
    body text NOT NULL CHECK (char_length(body) <= 100000),
    covered_from date,
    covered_to date,
    action text NOT NULL CHECK (action IN ('CREATE', 'SAVE', 'DELETE', 'RESTORE')),
    saved_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    source_revision bigint,
    deleted boolean NOT NULL,
    PRIMARY KEY (entry_id, revision),
    FOREIGN KEY (entry_id, owner_id) REFERENCES diary.entry(id, owner_id),
    CHECK ((covered_from IS NULL AND covered_to IS NULL)
        OR (covered_from IS NOT NULL AND covered_to IS NOT NULL AND covered_from <= covered_to)),
    CHECK (source_revision IS NULL OR (source_revision > 0 AND source_revision < revision))
);
CREATE TABLE IF NOT EXISTS diary.mutation (
    owner_id bigint NOT NULL CHECK (owner_id > 0),
    mutation_id uuid NOT NULL,
    entry_id uuid NOT NULL,
    action text NOT NULL CHECK (action IN ('CREATE', 'SAVE', 'DELETE', 'RESTORE')),
    request_digest text NOT NULL CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    result_revision bigint NOT NULL CHECK (result_revision > 0),
    saved_at timestamptz NOT NULL,
    PRIMARY KEY (owner_id, mutation_id),
    FOREIGN KEY (entry_id, owner_id) REFERENCES diary.entry(id, owner_id)
);
CREATE INDEX IF NOT EXISTS entry_updated_idx ON diary.entry(owner_id, updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS entry_created_idx ON diary.entry(owner_id, created_at, id);
CREATE INDEX IF NOT EXISTS entry_covered_idx ON diary.entry(owner_id, covered_from, covered_to);

ALTER TABLE diary.entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE diary.entry FORCE ROW LEVEL SECURITY;
ALTER TABLE diary.entry_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE diary.entry_revision FORCE ROW LEVEL SECURITY;
ALTER TABLE diary.mutation ENABLE ROW LEVEL SECURITY;
ALTER TABLE diary.mutation FORCE ROW LEVEL SECURITY;
DO $$
DECLARE tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['entry', 'entry_revision', 'mutation'] LOOP
        IF NOT EXISTS (SELECT FROM pg_policies WHERE schemaname = 'diary'
                       AND tablename = tbl AND policyname = 'diary_owner') THEN
            EXECUTE format('CREATE POLICY diary_owner ON diary.%I TO diary_runtime '
                || 'USING (owner_id = nullif(current_setting(''diary.owner_id'', true), '''')::bigint) '
                || 'WITH CHECK (owner_id = nullif(current_setting(''diary.owner_id'', true), '''')::bigint)', tbl);
        END IF;
    END LOOP;
END $$;
REVOKE ALL ON ALL TABLES IN SCHEMA diary FROM PUBLIC, diary_runtime;
GRANT SELECT, INSERT, UPDATE ON diary.entry TO diary_runtime;
GRANT SELECT, INSERT ON diary.entry_revision, diary.mutation TO diary_runtime;

-- 检查既有结构，重复执行不掩盖表/约束/索引/策略变化。失败会回滚本事务。
DO $$
DECLARE actual text; expected text;
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'diary_runtime'
               AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls OR rolinherit))
       OR EXISTS (SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                  JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='diary' AND r.rolname='diary_runtime') THEN
        RAISE EXCEPTION 'Diary runtime role must not bypass isolation or own tables';
    END IF;
    SELECT md5(string_agg(def, E'\n' ORDER BY def)) INTO actual FROM (
        SELECT 'column:'||c.relname||':'||a.attname||':'||format_type(a.atttypid,a.atttypmod)
            ||':'||a.attnotnull::text||':'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') AS def
        FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
        LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
        WHERE n.nspname='diary' AND c.relkind='r'
        UNION ALL SELECT 'constraint:'||c.relname||':'||pg_get_constraintdef(k.oid)
        FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid
        JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='diary'
        UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='diary'
        UNION ALL SELECT 'policy:'||tablename||':'||policyname||':'||roles::text||':'||cmd||':'||qual||':'||with_check
        FROM pg_policies WHERE schemaname='diary'
        UNION ALL SELECT 'rls:'||c.relname||':'||c.relrowsecurity::text||':'||c.relforcerowsecurity::text
        FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='diary' AND c.relkind='r'
    ) shape;
    SELECT fingerprint INTO expected FROM diary.schema_version WHERE version=1;
    IF expected IS NULL THEN
        INSERT INTO diary.schema_version(version,fingerprint) VALUES (1,actual);
    ELSIF expected <> actual THEN
        RAISE EXCEPTION 'Diary schema drift detected; migration refused';
    END IF;
END $$;
COMMIT;
