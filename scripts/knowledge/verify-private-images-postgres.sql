\set ON_ERROR_STOP on
BEGIN;
SET LOCAL ROLE knowledge_runtime;
SELECT set_config('knowledge.owner_id','1',true);
INSERT INTO knowledge.owner_control(owner_id) VALUES (1) ON CONFLICT DO NOTHING;
INSERT INTO knowledge.entry(owner_id,id,kind,title,body,revision)
VALUES (1,'11111111-1111-4111-8111-111111111111','NOTE','QA image','',1);
INSERT INTO knowledge.image(owner_id,id,entry_id,mutation_id,request_hash,object_key,mime,size_bytes,width,height,state)
VALUES (1,'22222222-2222-4222-8222-222222222222','11111111-1111-4111-8111-111111111111',
        '33333333-3333-4333-8333-333333333333',repeat('a',64),
        'knowledge/private/objects/qa-image','image/png',128,8,8,'ACTIVE');
DO $$ BEGIN
  IF (SELECT count(*) FROM knowledge.image WHERE owner_id=1)<>1 THEN RAISE EXCEPTION 'owner image missing'; END IF;
END $$;
SELECT set_config('knowledge.owner_id','2',true);
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM knowledge.image) THEN RAISE EXCEPTION 'cross-owner image visible'; END IF;
END $$;
SELECT set_config('knowledge.owner_id','',true);
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM knowledge.image) THEN RAISE EXCEPTION 'ownerless image visible'; END IF;
END $$;
ROLLBACK;
