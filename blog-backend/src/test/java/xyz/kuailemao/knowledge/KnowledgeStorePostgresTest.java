package xyz.kuailemao.knowledge;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static xyz.kuailemao.knowledge.KnowledgeModels.*;

@EnabledIfEnvironmentVariable(named="BLOG_KNOWLEDGE_QA",matches="true")
class KnowledgeStorePostgresTest {
    HikariDataSource pool;
    KnowledgeStore store;
    @BeforeEach void setup(){
        pool=new HikariDataSource();pool.setJdbcUrl("jdbc:postgresql://127.0.0.1:15433/knowledge_qa");
        pool.setUsername("knowledge_runtime");pool.setPassword("isolated-knowledge-runtime");
        pool.setMaximumPoolSize(1);pool.setMinimumIdle(0);
        store=new KnowledgeStore(new NamedParameterJdbcTemplate(pool),new DataSourceTransactionManager(pool));
    }
    @AfterEach void close(){pool.close();}
    Content content(String body){return new Content(Kind.NOTE,"中文资料 %_ '",body,"https://example.com/source",null,List.of());}
    Write write(UUID id,long revision,String body){return new Write(UUID.randomUUID(),id,revision,content(body));}
    @Test void retriesConflictsHistoryAndRollback(){
        long owner=100;UUID id=UUID.randomUUID();Write first=write(id,0,"初稿");
        Ack saved=store.save(owner,first);assertEquals(saved,store.save(owner,first));
        assertEquals(409,assertThrows(KnowledgeFailure.class,()->store.save(owner,new Write(first.mutationId(),id,0,content("不同内容")))).status());
        store.save(owner,write(id,1,"第二版"));
        assertEquals(409,assertThrows(KnowledgeFailure.class,()->store.save(owner,write(id,1,"旧窗口"))).status());
        assertEquals(2,store.history(owner,id,1).total());
        assertThrows(KnowledgeFailure.class,()->store.tx(owner,()->{store.save(owner,write(id,2,"回滚内容"));throw KnowledgeFailure.invalid("fixture");}));
        assertEquals("第二版",store.read(owner,id,false).content().body());
        assertEquals(2,store.history(owner,id,1).total());
        store.action(owner,id,new Action(UUID.randomUUID(),2,1L),"RECOVER");
        assertEquals("初稿",store.read(owner,id,false).content().body());
    }
    @Test void ownerContextDoesNotLeakAcrossPooledTransactions(){
        UUID id=UUID.randomUUID();store.save(201,write(id,0,"secret"));
        assertEquals(404,assertThrows(KnowledgeFailure.class,()->store.read(202,id,false)).status());
        assertEquals(0,store.jdbc.queryForObject("SELECT count(*) FROM knowledge.entry",Map.of(),Integer.class));
        assertEquals("secret",store.read(201,id,false).content().body());
        assertThrows(Exception.class,()->store.jdbc.queryForObject("SELECT count(*) FROM diary.canary",Map.of(),Long.class));
        assertTrue(store.available(201));
    }
    @Test void deletedEntriesDisappearAndRestoreWithNewRevision(){
        long owner=300;UUID id=UUID.randomUUID();store.save(owner,write(id,0,"正文"));
        Action remove=new Action(UUID.randomUUID(),1,null);Ack ack=store.action(owner,id,remove,"DELETE");
        assertEquals(ack,store.action(owner,id,remove,"DELETE"));
        assertEquals(404,assertThrows(KnowledgeFailure.class,()->store.read(owner,id,false)).status());
        store.action(owner,id,new Action(UUID.randomUUID(),2,null),"RESTORE");
        assertEquals(3,store.read(owner,id,false).revision());
    }
    @Test void expiredTrashPurgesOnlyAfterThirtyDaysAndRemovesHistory(){
        long owner=301;UUID id=UUID.randomUUID();store.save(owner,write(id,0,"应被清理"));
        store.action(owner,id,new Action(UUID.randomUUID(),1,null),"DELETE");
        assertEquals(0,store.purgeExpired(owner).purged());
        store.tx(owner,()->{store.jdbc.update("UPDATE knowledge.entry SET deleted_at=now()-interval '31 days' WHERE owner_id=:owner AND id=:id",KnowledgeStore.params(owner,id));return null;});
        assertEquals(1,store.purgeExpired(owner).purged());
        assertEquals(404,assertThrows(KnowledgeFailure.class,()->store.read(owner,id,true)).status());
        assertEquals(0,store.tx(owner,()->store.jdbc.queryForObject("SELECT count(*) FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:id",KnowledgeStore.params(owner,id),Integer.class)));
    }
    @Test void searchTreatsWildcardsLiterallyAndListDoesNotReturnBodies(){
        long owner=400;UUID id=UUID.randomUUID();store.save(owner,write(id,0,"中文段落"));
        var filter=new Filter(null,null,null,null,null,"%_ '",1,20,false);
        var result=store.list(owner,filter);assertEquals(1,result.total());
        assertEquals("",result.records().get(0).content().body());
        assertEquals("中文段落",store.read(owner,id,false).content().body());
        var hit=store.search(owner,new Filter(null,null,null,null,null,"中文",1,20,false));
        assertEquals("中文段落",hit.records().get(0).excerpt());
        assertEquals("",hit.records().get(0).entry().content().body());
    }
    @Test void labelsCannotBeDeletedWhileInUse(){
        long owner=500;UUID tag=UUID.randomUUID();store.saveLabel(owner,new LabelWrite(tag,"TAG","测试",0));
        UUID id=UUID.randomUUID();store.save(owner,new Write(UUID.randomUUID(),id,0,new Content(Kind.CONCEPT,"概念","内容","",null,List.of(tag))));
        assertEquals(400,assertThrows(KnowledgeFailure.class,()->store.deleteLabel(owner,tag,1)).status());
        assertEquals("内容",store.read(owner,id,false).content().body());
    }
    @Test void searchRanksTitleThenTagBeforeBodyAndPaginatesWithoutDuplicates(){
        long owner=600;UUID title=UUID.randomUUID(),tagged=UUID.randomUUID(),body=UUID.randomUUID(),tag=UUID.randomUUID();
        store.save(owner,write(body,0,"知识关联 正文匹配"));
        store.saveLabel(owner,new LabelWrite(tag,"TAG","知识关联",0));
        store.save(owner,new Write(UUID.randomUUID(),tagged,0,new Content(Kind.NOTE,"标签匹配","资料正文","",null,List.of(tag))));
        store.save(owner,new Write(UUID.randomUUID(),title,0,new Content(Kind.NOTE,"知识关联 标题匹配","资料正文","",null,List.of())));
        List<UUID> ids=new ArrayList<>();
        for(int page=1;page<=3;page++){
            var result=store.search(owner,new Filter(null,null,null,null,null,"知识关联",page,1,false));
            assertEquals(3,result.total());assertEquals(1,result.records().size());ids.add(result.records().get(0).entry().id());
        }
        assertEquals(List.of(title,tagged,body),ids);
    }
    @Test void manualRelationsAreCanonicalVersionedAndOwnerScoped(){
        long owner=700;UUID a=UUID.randomUUID(),b=UUID.randomUUID(),relation=UUID.randomUUID();
        store.save(owner,write(a,0,"概念一"));store.save(owner,write(b,0,"概念二"));
        var modules=mock(xyz.kuailemao.service.SiteModuleAccessService.class);
        var articles=mock(xyz.kuailemao.mapper.ArticleMapper.class);
        var access=mock(KnowledgeAccess.class);
        when(access.canManage()).thenReturn(false);
        var links=new KnowledgeRelations(store,articles,modules,access);
        Endpoint left=new Endpoint("ENTRY",a.toString()),right=new Endpoint("ENTRY",b.toString());
        Relation first=links.save(owner,new RelationWrite(relation,0,left,right,RelationType.RELATED_TO,"关联说明"));
        assertEquals(1,first.revision());
        assertTrue(first.from().key().compareTo(first.to().key())<0);
        assertEquals(409,assertThrows(KnowledgeFailure.class,()->links.save(owner,new RelationWrite(UUID.randomUUID(),0,right,left,RelationType.RELATED_TO,"重复"))).status());
        assertEquals(400,assertThrows(KnowledgeFailure.class,()->links.save(owner,new RelationWrite(UUID.randomUUID(),0,left,left,RelationType.EXPLAINS,"自环"))).status());
        assertEquals(400,assertThrows(KnowledgeFailure.class,()->links.save(owner,new RelationWrite(UUID.randomUUID(),0,left,new Endpoint("ENTRY",UUID.randomUUID().toString()),RelationType.EXPLAINS,"伪造"))).status());
        assertEquals(1,links.neighbors(owner,left,1,20).total());
        assertEquals(404,assertThrows(KnowledgeFailure.class,()->links.neighbors(owner+1,left,1,20)).status());
        Relation updated=links.save(owner,new RelationWrite(relation,1,right,left,RelationType.RELATED_TO,"改过的说明"));
        assertEquals(2,updated.revision());
        links.delete(owner,relation,2);
        assertEquals(0,links.neighbors(owner,left,1,20).total());
    }
    @Test void articleRelationsFollowLiveBlogVisibility(){
        long owner=701;UUID entry=UUID.randomUUID(),relation=UUID.randomUUID();store.save(owner,write(entry,0,"正文"));
        var modules=mock(xyz.kuailemao.service.SiteModuleAccessService.class);
        var articles=mock(xyz.kuailemao.mapper.ArticleMapper.class);
        var access=mock(KnowledgeAccess.class);
        when(modules.canView("blog")).thenReturn(true);
        var article=xyz.kuailemao.domain.entity.Article.builder().id(42L).userId(owner).status(1).isDeleted(0).articleTitle("公开文").build();
        when(articles.selectById(42L)).thenReturn(article);
        var links=new KnowledgeRelations(store,articles,modules,access);
        Endpoint knowledge=new Endpoint("ENTRY",entry.toString()),blog=new Endpoint("ARTICLE","42");
        links.save(owner,new RelationWrite(relation,0,knowledge,blog,RelationType.REFERENCES,"引用"));
        assertEquals(1,links.neighbors(owner,knowledge,1,20).total());
        assertEquals(2,links.graph(owner,knowledge,1,50,100).nodes().size());
        article.setStatus(3);
        assertEquals(0,links.neighbors(owner,knowledge,1,20).total());
        assertEquals(1,links.graph(owner,knowledge,1,50,100).nodes().size());
        article.setStatus(2);
        assertEquals(0,links.neighbors(owner,knowledge,1,20).total());
        article.setStatus(1);article.setIsDeleted(1);
        assertEquals(0,links.neighbors(owner,knowledge,1,20).total());
        article.setIsDeleted(0);when(modules.canView("blog")).thenReturn(false);
        assertEquals(0,links.neighbors(owner,knowledge,1,20).total());
    }
    @Test void localGraphCapsVisibleNodesAndNeverReturnsDeletedEndpoints(){
        long owner=702;UUID center=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        for(UUID id:List.of(center,a,b))store.save(owner,write(id,0,"内容"));
        var links=new KnowledgeRelations(store,mock(xyz.kuailemao.mapper.ArticleMapper.class),
                mock(xyz.kuailemao.service.SiteModuleAccessService.class),mock(KnowledgeAccess.class));
        Endpoint root=new Endpoint("ENTRY",center.toString());
        for(UUID id:List.of(a,b))links.save(owner,new RelationWrite(UUID.randomUUID(),0,root,new Endpoint("ENTRY",id.toString()),RelationType.EXPLAINS,"说明"));
        UUID secondHop=UUID.randomUUID();store.save(owner,write(secondHop,0,"二跳"));
        links.save(owner,new RelationWrite(UUID.randomUUID(),0,new Endpoint("ENTRY",a.toString()),new Endpoint("ENTRY",secondHop.toString()),RelationType.DEPENDS_ON,"二跳关系"));
        GraphView small=links.graph(owner,root,1,2,100);
        assertEquals(2,small.nodes().size());assertEquals(1,small.edges().size());assertTrue(small.hasMore());
        assertEquals(3,links.graph(owner,root,1,50,100).nodes().size());
        assertEquals(4,links.graph(owner,root,2,50,100).nodes().size());
        assertEquals(2,links.neighbors(owner,root,1,1).total());
        assertEquals(1,links.neighbors(owner,root,2,1).records().size());
        store.action(owner,a,new Action(UUID.randomUUID(),1,null),"DELETE");
        GraphView remaining=links.graph(owner,root,1,50,100);
        assertEquals(2,remaining.nodes().size());assertEquals(1,remaining.edges().size());
    }
    @Test void onlyKnowledgeOwnerWithArticlePermissionSeesOwnDraftEndpoint(){
        long owner=703;UUID id=UUID.randomUUID();store.save(owner,write(id,0,"正文"));
        var modules=mock(xyz.kuailemao.service.SiteModuleAccessService.class);when(modules.canView("blog")).thenReturn(true);
        var articles=mock(xyz.kuailemao.mapper.ArticleMapper.class);
        var article=xyz.kuailemao.domain.entity.Article.builder().id(43L).userId(owner).status(3).isDeleted(0).articleTitle("未发布草稿").build();
        when(articles.selectById(43L)).thenReturn(article);
        var access=mock(KnowledgeAccess.class);when(access.canManage()).thenReturn(true);
        var links=new KnowledgeRelations(store,articles,modules,access);
        Endpoint root=new Endpoint("ENTRY",id.toString());
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("qa","qa",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("blog:publish:article")));
        try{
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            links.save(owner,new RelationWrite(UUID.randomUUID(),0,root,new Endpoint("ARTICLE","43"),RelationType.REFERENCES,"草稿"));
            assertEquals(1,links.neighbors(owner,root,1,20).total());
            when(access.canManage()).thenReturn(false);
            assertEquals(0,links.neighbors(owner,root,1,20).total());
        }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void draftSnapshotIsPrivateStableAndIdempotentAcrossPgWriteback(){
        long owner=704;UUID entry=UUID.randomUUID(),exportId=UUID.randomUUID();store.save(owner,write(entry,0,"用于文章的合成知识"));
        var modules=mock(xyz.kuailemao.service.SiteModuleAccessService.class);when(modules.canView("blog")).thenReturn(true);
        var writer=mock(KnowledgeArticleDraftWriter.class);
        var service=new KnowledgeDraftExport(store,writer,modules);
        var source=new KnowledgeDraftExport.Source(entry,1,null,null);
        var request=new KnowledgeDraftExport.Request(exportId,"合成草稿","人工整理的正文",1,List.of(1L),"https://example.com/cover.png",List.of(source));
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("qa","qa",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("blog:publish:article")));
        try{
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            var first=service.prepare(owner,request);assertEquals("PREPARED",first.state());
            assertEquals("用于文章的合成知识",first.snapshot().sources().get(0).text());
            assertEquals(first,service.prepare(owner,request));
            var changed=new KnowledgeDraftExport.Request(exportId,"不同标题",request.body(),1,List.of(1L),request.coverUrl(),List.of(source));
            assertEquals(409,assertThrows(KnowledgeFailure.class,()->service.prepare(owner,changed)).status());
            store.action(owner,entry,new Action(UUID.randomUUID(),1,null),"DELETE");
            assertEquals("用于文章的合成知识",service.prepare(owner,request).snapshot().sources().get(0).text());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("qa","qa",List.of()));
            assertEquals(403,assertThrows(KnowledgeFailure.class,()->service.create(owner,request)).status());
            verifyNoInteractions(writer);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            when(writer.create(eq(owner),eq(exportId),anyString(),any(xyz.kuailemao.domain.dto.ArticleDTO.class))).thenReturn(99L);
            var created=service.create(owner,request);assertEquals("CREATED",created.state());assertEquals(99L,created.articleId());
            assertEquals("CREATED",service.read(owner,exportId).state());
            assertEquals(99L,service.create(owner,request).articleId());
            verify(writer,times(2)).create(eq(owner),eq(exportId),anyString(),any(xyz.kuailemao.domain.dto.ArticleDTO.class));
            assertEquals(404,assertThrows(KnowledgeFailure.class,()->service.read(owner+1,exportId)).status());
            store.action(owner,entry,new Action(UUID.randomUUID(),2,null),"RESTORE");
            UUID deletedExport=UUID.randomUUID();
            var deletedRequest=new KnowledgeDraftExport.Request(deletedExport,request.title(),request.body(),1,List.of(1L),request.coverUrl(),List.of(source));
            when(writer.create(eq(owner),eq(deletedExport),anyString(),any(xyz.kuailemao.domain.dto.ArticleDTO.class)))
                    .thenThrow(new KnowledgeFailure(410,"已生成的草稿被删除，不能自动再建一篇"));
            assertEquals(410,assertThrows(KnowledgeFailure.class,()->service.create(owner,deletedRequest)).status());
            assertEquals("DELETED",service.read(owner,deletedExport).state());
        }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
}
