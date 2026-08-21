SET NAMES utf8mb4;
UPDATE t_category SET category_name = '技术笔记' WHERE id = 14;
UPDATE t_tag SET tag_name = '随笔' WHERE id = 14;
SELECT id, category_name FROM t_category;
SELECT id, tag_name FROM t_tag;
