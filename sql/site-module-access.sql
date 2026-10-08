-- Back up the database before applying. Re-running preserves existing access policies.
CREATE TABLE IF NOT EXISTS sys_site_module (
  module_key VARCHAR(32) NOT NULL PRIMARY KEY,
  public_access TINYINT NOT NULL DEFAULT 1,
  revision INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS sys_site_module_role (
  module_key VARCHAR(32) NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (module_key, role_id),
  KEY idx_site_module_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO sys_site_module (module_key, public_access, revision) VALUES
('home',1,0),('experience',1,0),('blog',1,0),('shares',1,0),('photos',1,0),('about',1,0);
