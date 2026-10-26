package xyz.luyu.knowledge;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import xyz.luyu.domain.dto.ArticleDTO;
import xyz.luyu.domain.entity.Article;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.mapper.ArticleMapper;
import xyz.luyu.mapper.KnowledgeArticleExportMapper;
import xyz.luyu.service.ArticleService;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="BLOG_KNOWLEDGE_MYSQL_QA",matches="true")
class KnowledgeArticleDraftMysqlTest {
    HikariDataSource pool;
    JdbcTemplate jdbc;
    KnowledgeArticleDraftWriter writer;
    KnowledgeArticleExportMapper mappings;
    ArticleMapper articles;
    ArticleService service;
    UUID failingExport;
    AtomicLong lastInserted = new AtomicLong();

    @BeforeEach void setup() {
        pool=new HikariDataSource();
        pool.setJdbcUrl("jdbc:mysql://127.0.0.1:15434/knowledge_qa?useSSL=false&allowPublicKeyRetrieval=true");
        pool.setUsername("root");pool.setPassword("isolated-knowledge-mysql");pool.setMaximumPoolSize(2);
        jdbc=new JdbcTemplate(pool);
        mappings=mock(KnowledgeArticleExportMapper.class);
        when(mappings.find(anyLong(),any())).thenAnswer(invocation->{
            long owner=invocation.getArgument(0);String export=invocation.getArgument(1);
            return jdbc.query("SELECT owner_id,export_id,request_hash,article_id FROM t_knowledge_article_export WHERE owner_id=? AND export_id=? FOR UPDATE",
                    (r,n)->new KnowledgeArticleExportMapper.Mapping(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4)),owner,export)
                    .stream().findFirst().orElse(null);
        });
        when(mappings.insert(anyLong(),any(),any(),anyLong())).thenAnswer(invocation->{
            String export=invocation.getArgument(1);
            if(failingExport!=null && failingExport.toString().equals(export))throw new IllegalStateException("synthetic mapping write failure");
            return jdbc.update("INSERT INTO t_knowledge_article_export(owner_id,export_id,request_hash,article_id) VALUES(?,?,?,?)",
                    invocation.getArgument(0),export,invocation.getArgument(2),invocation.getArgument(3));
        });
        articles=mock(ArticleMapper.class);
        when(articles.selectById(anyLong())).thenAnswer(invocation->jdbc.query("SELECT id,user_id,status,is_deleted FROM t_article WHERE id=?",
                (r,n)->Article.builder().id(r.getLong(1)).userId(r.getLong(2)).status(r.getInt(3)).isDeleted(r.getInt(4)).build(),
                (Long)invocation.getArgument(0)).stream().findFirst().orElse(null));
        service=mock(ArticleService.class);
        when(service.publish(any())).thenAnswer(invocation->{
            ArticleDTO draft=invocation.getArgument(0);
            lastInserted.set(draft.getId());
            jdbc.update("INSERT INTO t_article(id,user_id,status,is_deleted) VALUES(?,?,?,0)",draft.getId(),7L,draft.getStatus());
            return ResponseResult.success();
        });
        var target=new KnowledgeArticleDraftWriter(mappings,articles,service);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(pool),new AnnotationTransactionAttributeSource()));
        writer=(KnowledgeArticleDraftWriter)proxy.getProxy();
    }
    @AfterEach void cleanup(){pool.close();}
    private ArticleDTO draft(){var value=new ArticleDTO();value.setStatus(3);return value;}

    @Test void articleAndMappingCommitTogetherAndRetryReturnsSameId(){
        UUID export=UUID.randomUUID();
        long articleId=writer.create(7,export,"a".repeat(64),draft());
        assertEquals(articleId,writer.create(7,export,"a".repeat(64),draft()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM t_article WHERE id=?",Integer.class,articleId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM t_knowledge_article_export WHERE article_id=?",Integer.class,articleId));
        verify(service,times(1)).publish(any());
        assertEquals(409,assertThrows(KnowledgeFailure.class,()->writer.create(7,export,"b".repeat(64),draft())).status());
        jdbc.update("UPDATE t_article SET is_deleted=1 WHERE id=?",articleId);
        assertEquals(410,assertThrows(KnowledgeFailure.class,()->writer.create(7,export,"a".repeat(64),draft())).status());
    }
    @Test void mappingFailureRollsBackArticleInsertion(){
        failingExport=UUID.randomUUID();
        assertThrows(IllegalStateException.class,()->writer.create(7,failingExport,"c".repeat(64),draft()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM t_article WHERE id=?",Integer.class,lastInserted.get()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM t_knowledge_article_export WHERE export_id=?",Integer.class,failingExport.toString()));
    }
}
