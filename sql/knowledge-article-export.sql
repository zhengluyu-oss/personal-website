-- Apply with the normal MySQL migration identity before enabling knowledge exports.
-- A unique owner/export ID makes a repeated cross-database request return the same draft.
CREATE TABLE IF NOT EXISTS t_knowledge_article_export (
  owner_id BIGINT NOT NULL,
  export_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  article_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (owner_id, export_id),
  UNIQUE KEY knowledge_export_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
