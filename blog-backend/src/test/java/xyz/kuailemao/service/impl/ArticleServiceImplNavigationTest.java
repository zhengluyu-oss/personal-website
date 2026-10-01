package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.entity.Article;
import xyz.kuailemao.domain.entity.Category;
import xyz.kuailemao.domain.vo.ArticleDetailVO;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.mapper.ArticleTagMapper;
import xyz.kuailemao.mapper.CategoryMapper;
import xyz.kuailemao.mapper.TagMapper;
import xyz.kuailemao.service.CommentService;
import xyz.kuailemao.service.FavoriteService;
import xyz.kuailemao.service.LikeService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceImplNavigationTest {

    @Mock private ArticleMapper articleMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private ArticleTagMapper articleTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private CommentService commentService;
    @Mock private LikeService likeService;
    @Mock private FavoriteService favoriteService;

    private ArticleServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Article.class);
        service = new ArticleServiceImpl();
        ReflectionTestUtils.setField(service, "articleMapper", articleMapper);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "articleTagMapper", articleTagMapper);
        ReflectionTestUtils.setField(service, "tagMapper", tagMapper);
        ReflectionTestUtils.setField(service, "commentService", commentService);
        ReflectionTestUtils.setField(service, "likeService", likeService);
        ReflectionTestUtils.setField(service, "favoriteService", favoriteService);
    }

    @Test
    void bothNavigationQueriesOnlySelectPublicArticles() {
        Article current = Article.builder().id(10L).status(1).categoryId(2L).articleTitle("当前文章").build();
        Article previous = Article.builder().id(7L).status(1).articleTitle("上一篇公开文章").build();
        Article next = Article.builder().id(13L).status(1).articleTitle("下一篇公开文章").build();
        Category category = new Category();
        category.setId(2L);
        category.setCategoryName("技术");
        when(categoryMapper.selectById(2L)).thenReturn(category);
        when(articleTagMapper.selectList(any())).thenReturn(List.of());
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of());

        AtomicInteger calls = new AtomicInteger();
        List<String> navigationSql = new ArrayList<>();
        when(articleMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<Article> query = invocation.getArgument(0);
            int call = calls.getAndIncrement();
            if (call == 0) return current;
            navigationSql.add(query.getSqlSegment());
            return call == 1 ? previous : next;
        });

        ArticleDetailVO result = service.getArticleDetail(10);

        assertEquals(7L, result.getPreArticleId());
        assertEquals("上一篇公开文章", result.getPreArticleTitle());
        assertEquals(13L, result.getNextArticleId());
        assertEquals("下一篇公开文章", result.getNextArticleTitle());
        assertEquals(2, navigationSql.size());
        assertTrue(navigationSql.stream().allMatch(sql -> sql.contains("status")), navigationSql.toString());
    }
}
