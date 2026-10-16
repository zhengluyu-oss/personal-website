-- 管理员显式执行；软件包/镜像必须先提供经验证的 pgvector，应用不安装扩展。
BEGIN;
DO $$ BEGIN
 IF EXISTS (SELECT FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace WHERE e.extname='vector' AND n.nspname<>'diary_vector') THEN
  RAISE EXCEPTION 'Existing vector extension schema conflicts; do not relocate automatically';
 END IF;
END $$;
CREATE SCHEMA IF NOT EXISTS diary_vector AUTHORIZATION diary_migrator;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA diary_vector;
REVOKE ALL ON SCHEMA diary_vector FROM PUBLIC;
GRANT USAGE ON SCHEMA diary_vector TO diary_runtime,diary_migrator;
COMMIT;
