-- Run manually as administrator in a dedicated test/production knowledge database.
BEGIN;
DO $$ BEGIN
 IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='knowledge_migrator') THEN CREATE ROLE knowledge_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS; END IF;
 IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='knowledge_runtime') THEN CREATE ROLE knowledge_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS; END IF;
 IF EXISTS(SELECT FROM pg_roles WHERE rolname IN('knowledge_runtime','knowledge_migrator') AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls OR rolinherit))
 OR EXISTS(SELECT FROM pg_auth_members WHERE member IN(SELECT oid FROM pg_roles WHERE rolname IN('knowledge_runtime','knowledge_migrator'))) THEN RAISE EXCEPTION 'Unsafe knowledge role configuration'; END IF;
END $$;
COMMIT;
