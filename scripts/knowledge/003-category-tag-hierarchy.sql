\set ON_ERROR_STOP on
BEGIN;
SET LOCAL ROLE knowledge_migrator;
SET LOCAL statement_timeout='30s';
SELECT pg_advisory_xact_lock(1936876915,3);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM knowledge.schema_version WHERE version=2) THEN
    RAISE EXCEPTION 'Knowledge v2 schema is required';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM knowledge.schema_version WHERE version=3)
     AND EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema='knowledge' AND table_name='label' AND column_name='parent_category_id') THEN
    RAISE EXCEPTION 'Unversioned knowledge label hierarchy';
  END IF;
END $$;
SELECT EXISTS(SELECT 1 FROM knowledge.schema_version WHERE version=3) AS hierarchy_exists \gset
\if :hierarchy_exists
\else
ALTER TABLE knowledge.label ADD COLUMN parent_category_id uuid;
-- Forced RLS hides owner rows from the schema-owner role. The migration session
-- temporarily uses its original PostgreSQL administrator role for data repair.
RESET ROLE;

-- The existing owner has two categories. Every existing tag receives one parent.
UPDATE knowledge.label tag
   SET parent_category_id=category.id, revision=tag.revision+1
  FROM knowledge.label category
 WHERE tag.kind='TAG' AND category.owner_id=tag.owner_id AND category.kind='CATEGORY'
   AND category.name=CASE WHEN tag.name='高性价比人生指南' THEN '人生指南' ELSE '基础知识复习' END;
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM knowledge.label WHERE kind='TAG' AND parent_category_id IS NULL) THEN
    RAISE EXCEPTION 'Some tags have no matching parent category';
  END IF;
  IF EXISTS(
    SELECT 1 FROM knowledge.entry_tag et
      JOIN knowledge.entry e ON e.owner_id=et.owner_id AND e.id=et.entry_id
      JOIN knowledge.label tag ON tag.owner_id=et.owner_id AND tag.id=et.tag_id
     GROUP BY et.owner_id,et.entry_id,e.category_id
    HAVING count(DISTINCT tag.parent_category_id)<>1
        OR (e.category_id IS NOT NULL AND e.category_id IS DISTINCT FROM min(tag.parent_category_id::text)::uuid)
  ) THEN RAISE EXCEPTION 'Existing entry/category/tag combinations conflict'; END IF;
END $$;

CREATE TEMP TABLE knowledge_category_repairs ON COMMIT DROP AS
SELECT et.owner_id,et.entry_id,min(tag.parent_category_id::text)::uuid AS category_id
  FROM knowledge.entry_tag et
  JOIN knowledge.entry e ON e.owner_id=et.owner_id AND e.id=et.entry_id
  JOIN knowledge.label tag ON tag.owner_id=et.owner_id AND tag.id=et.tag_id
 WHERE e.category_id IS NULL
 GROUP BY et.owner_id,et.entry_id;
UPDATE knowledge.entry e
   SET category_id=fix.category_id,revision=e.revision+1,updated_at=now()
  FROM knowledge_category_repairs fix
 WHERE e.owner_id=fix.owner_id AND e.id=fix.entry_id;
INSERT INTO knowledge.entry_revision
    (owner_id,entry_id,revision,content,current_file_version,saved_at,deleted_at,action)
SELECT e.owner_id,e.id,e.revision,
       jsonb_build_object('kind',e.kind,'title',e.title,'body',e.body,
                          'sourceUrl',e.source_url,'categoryId',e.category_id,
                          'tagIds',coalesce((SELECT jsonb_agg(et.tag_id ORDER BY et.tag_id)
                                               FROM knowledge.entry_tag et
                                              WHERE et.owner_id=e.owner_id AND et.entry_id=e.id),'[]'::jsonb)),
       e.current_file_version,e.updated_at,e.deleted_at,'SAVE'
  FROM knowledge_category_repairs fix
  JOIN knowledge.entry e ON e.owner_id=fix.owner_id AND e.id=fix.entry_id;
SET LOCAL ROLE knowledge_migrator;

ALTER TABLE knowledge.label
  ADD CONSTRAINT label_parent_shape CHECK (
    (kind='CATEGORY' AND parent_category_id IS NULL) OR
    (kind='TAG' AND parent_category_id IS NOT NULL));
ALTER TABLE knowledge.label
  ADD CONSTRAINT label_parent_fkey FOREIGN KEY(owner_id,parent_category_id)
    REFERENCES knowledge.label(owner_id,id);
CREATE INDEX label_by_parent ON knowledge.label(owner_id,parent_category_id,name)
  WHERE kind='TAG';

CREATE FUNCTION knowledge.check_label_parent() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.kind='TAG' AND NOT EXISTS(
    SELECT 1 FROM knowledge.label
     WHERE owner_id=NEW.owner_id AND id=NEW.parent_category_id AND kind='CATEGORY'
  ) THEN RAISE EXCEPTION 'Tag parent must be a category'; END IF;
  IF TG_OP='UPDATE' THEN
    IF OLD.kind='CATEGORY' AND NEW.kind<>'CATEGORY'
       AND EXISTS(SELECT 1 FROM knowledge.label
                   WHERE owner_id=OLD.owner_id AND parent_category_id=OLD.id) THEN
      RAISE EXCEPTION 'Category still has tags';
    END IF;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER label_parent_guard BEFORE INSERT OR UPDATE ON knowledge.label
  FOR EACH ROW EXECUTE FUNCTION knowledge.check_label_parent();
\endif

DO $$ DECLARE actual text; BEGIN
  SELECT md5(string_agg(def,E'\n' ORDER BY def)) INTO actual FROM (
    SELECT 'column:'||a.attname||':'||format_type(a.atttypid,a.atttypmod)||':'||a.attnotnull||':'||coalesce(pg_get_expr(d.adbin,d.adrelid),'') AS def
      FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
     WHERE a.attrelid='knowledge.label'::regclass AND a.attnum>0 AND NOT a.attisdropped
    UNION ALL SELECT 'constraint:'||pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='knowledge.label'::regclass
    UNION ALL SELECT 'index:'||indexdef FROM pg_indexes WHERE schemaname='knowledge' AND tablename='label'
    UNION ALL SELECT 'trigger:'||pg_get_triggerdef(oid) FROM pg_trigger WHERE tgrelid='knowledge.label'::regclass AND NOT tgisinternal
    UNION ALL SELECT 'function:'||pg_get_functiondef('knowledge.check_label_parent()'::regprocedure)
  ) shape;
  INSERT INTO knowledge.schema_version(version,fingerprint) VALUES(3,actual)
    ON CONFLICT(version) DO NOTHING;
  IF (SELECT fingerprint FROM knowledge.schema_version WHERE version=3) IS DISTINCT FROM actual THEN
    RAISE EXCEPTION 'Knowledge label hierarchy schema drift';
  END IF;
END $$;
COMMIT;
