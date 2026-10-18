-- Back up sys_site_module and sys_site_module_role first. Safe to repeat; preserves saved policies.
INSERT IGNORE INTO sys_site_module (module_key, public_access, revision) VALUES ('diary',0,0);
