package xyz.luyu.knowledge;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static xyz.luyu.knowledge.KnowledgeModels.*;

@EnabledIfEnvironmentVariable(named="BLOG_KNOWLEDGE_QA",matches="true")
class KnowledgeTagOrderPostgresTest {
    KnowledgeStorePostgresTest fixture;KnowledgeStore store;KnowledgeTagOrder order;long owner;UUID a,b;
    @BeforeEach void setup(){fixture=new KnowledgeStorePostgresTest();fixture.setup();store=fixture.store;order=new KnowledgeTagOrder(store);owner=(UUID.randomUUID().getLeastSignificantBits()&0xfffffff)+9000;
        UUID category=UUID.randomUUID();store.saveLabel(owner,new LabelWrite(category,"CATEGORY","分类",null,0));
        a=UUID.randomUUID();b=UUID.randomUUID();store.saveLabel(owner,new LabelWrite(a,"TAG","标签A",category,0));store.saveLabel(owner,new LabelWrite(b,"TAG","标签B",category,0));}
    @AfterEach void close(){fixture.close();}
    UUID entry(String title,List<UUID> tags){UUID id=UUID.randomUUID();UUID category=store.labels(owner).stream().filter(l->l.id().equals(a)).findFirst().orElseThrow().parentCategoryId();store.save(owner,new Write(UUID.randomUUID(),id,0,new Content(Kind.NOTE,title,"正文","",category,tags)));return id;}
    @Test void independentTagsContinuousNumbersAndFilteredPagesUsePersistedOrder(){
        UUID x=entry("第一",List.of(a,b)),y=entry("第二",List.of(a,b)),z=entry("第三",List.of(a));
        var before=order.read(owner,a);var move=new KnowledgeTagOrder.Move(UUID.randomUUID(),z,1,before.digest());
        var after=order.move(owner,a,move);assertEquals(List.of(z,x,y),after.entryIds());assertEquals(List.of(x,y),order.read(owner,b).entryIds());
        assertEquals(after,order.move(owner,a,move));
        assertEquals(1,order.numbers(owner,List.of(z,x)).get(z).get(a));assertEquals(2,order.numbers(owner,List.of(x)).get(x).get(a));assertEquals(1,order.numbers(owner,List.of(x)).get(x).get(b));
        assertEquals(z,store.list(owner,new Filter(null,null,a,null,null,null,1,1,false)).records().get(0).id());
        assertEquals(x,store.list(owner,new Filter(null,null,a,null,null,"第一",1,20,false)).records().get(0).id());
        assertEquals(x,store.list(owner,new Filter(null,null,a,null,null,null,2,1,false)).records().get(0).id());
        var e=store.read(owner,z,false);store.save(owner,new Write(UUID.randomUUID(),z,e.revision(),new Content(Kind.NOTE,"改标题","改内容","",e.content().categoryId(),e.content().tagIds())));
        assertEquals(after.entryIds(),order.read(owner,a).entryIds());
        e=store.read(owner,z,false);store.action(owner,z,new Action(UUID.randomUUID(),e.revision(),null),"DELETE");
        assertEquals(List.of(x,y),order.read(owner,a).entryIds());assertEquals(1,order.numbers(owner,List.of(x)).get(x).get(a));
    }
    @Test void staleMovesForeignEntriesAndCrossOwnerReadsCannotChangeOrder(){
        UUID x=entry("一",List.of(a)),y=entry("二",List.of(a)),other=entry("其他标签",List.of(b));
        var before=order.read(owner,a);order.move(owner,a,new KnowledgeTagOrder.Move(UUID.randomUUID(),y,1,before.digest()));
        assertEquals(409,assertThrows(KnowledgeFailure.class,()->order.move(owner,a,new KnowledgeTagOrder.Move(UUID.randomUUID(),x,1,before.digest()))).status());
        var now=order.read(owner,a);
        assertEquals(400,assertThrows(KnowledgeFailure.class,()->order.move(owner,a,new KnowledgeTagOrder.Move(UUID.randomUUID(),other,1,now.digest()))).status());
        assertEquals(404,assertThrows(KnowledgeFailure.class,()->order.read(owner+1,a)).status());
        assertTrue(order.numbers(owner+1,List.of(x)).isEmpty());
        assertEquals(0,store.jdbc.queryForObject("SELECT count(*) FROM knowledge_order.tag_order",Map.of(),Integer.class));
        UUID newEntry=entry("新增",List.of(a));assertEquals(List.of(y,x,newEntry),order.read(owner,a).entryIds());
    }
}
