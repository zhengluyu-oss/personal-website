-- 由数据库管理员通过 psql -v ON_ERROR_STOP=1 -f 执行；不设置密码，不授权给其他角色。
-- 首次部署后通过独立的秘密管理渠道设置运行角色密码及 LOGIN。
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'diary_migrator') THEN
        CREATE ROLE diary_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'diary_runtime') THEN
        CREATE ROLE diary_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname IN ('diary_runtime', 'diary_migrator')
               AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls OR rolinherit))
       OR EXISTS (SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.member
                  WHERE r.rolname = 'diary_runtime') THEN
        RAISE EXCEPTION 'Existing diary roles are unsafe; review privileges before migration';
    END IF;
END $$;
COMMIT;
