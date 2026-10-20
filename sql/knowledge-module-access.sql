-- Apply explicitly with the normal site-policy migration identity. No role grants.
INSERT IGNORE INTO sys_site_module(module_key,public_access,revision) VALUES('knowledge',0,0);
