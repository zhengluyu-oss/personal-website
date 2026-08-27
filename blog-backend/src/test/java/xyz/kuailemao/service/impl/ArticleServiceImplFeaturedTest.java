package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.entity.Article;
import xyz.kuailemao.domain.entity.Category;
import xyz.kuailemao.domain.entity.WebsiteInfo;
import xyz.kuailemao.domain.vo.BlogFeedVO;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.mapper.ArticleTagMapper;
import xyz.kuailemao.mapper.CategoryMapper;
import xyz.kuailemao.mapper.TagMapper;
import xyz.kuailemao.mapper.WebsiteInfoMapper;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceImplFeaturedTest {

    @Mock private ArticleMapper articleMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private ArticleTagMapper articleTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private WebsiteInfoMapper websiteInfoMapper;

    private ArticleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ArticleServiceImpl();
        ReflectionTestUtils.setField(service, "articleMapper", articleMapper);
        ReflectionTestUtils.setField(service, "baseMapper", articleMapper);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "articleTagMapper", articleTagMapper);
        ReflectionTestUtils.setField(service, "tagMapper", tagMapper);
        ReflectionTestUtils.setField(service, "websiteInfoMapper", websiteInfoMapper);
    }

    @Test
    void aggregationUsesConfiguredPublishedArticleAndKeepsItOutOfList() {
        Article featured = article(2L, 1L, 1, "指定推荐");
        Article ordinary = article(3L, 1L, 1, "普通文章");
        WebsiteInfo info = WebsiteInfo.builder().id(1L).blogFeaturedArticleId(2L).build();
        when(websiteInfoMapper.selectById(1L)).thenReturn(info);
        when(articleMapper.selectById(2L)).thenReturn(featured);
        stubFeedQueries(ordinary, 2L);

        BlogFeedVO result = service.listBlogFeed(null, 1, 20);

        assertNotNull(result.getFeaturedArticle());
        assertEquals(2L, result.getFeaturedArticle().getId());
        assertEquals(List.of(3L), result.getArticles().stream().map(item -> item.getId()).toList());
        assertEquals(2L, result.getTotal());
        assertEquals(1L, result.getListTotal());
    }

    @Test
    void invalidConfiguredArticleFallsBackToLatestPublishedArticle() {
        Article draft = article(2L, 1L, 3, "草稿");
        Article latest = article(4L, 1L, 1, "最新公开文章");
        WebsiteInfo info = WebsiteInfo.builder().id(1L).blogFeaturedArticleId(2L).build();
        when(websiteInfoMapper.selectById(1L)).thenReturn(info);
        when(articleMapper.selectById(2L)).thenReturn(draft);
        when(articleMapper.selectOne(any())).thenReturn(latest);
        stubFeedQueries(null, 1L);

        BlogFeedVO result = service.listBlogFeed(null, 1, 20);

        assertEquals(4L, result.getFeaturedArticle().getId());
        assertEquals(1L, result.getTotal());
        assertEquals(0L, result.getListTotal());
    }

    @Test
    void categoryUsesItsOwnConfiguredPublishedArticle() {
        Article featured = article(5L, 8L, 1, "分类指定推荐");
        Category category = new Category();
        category.setId(8L);
        category.setCategoryName("人工智能");
        category.setFeaturedArticleId(5L);
        when(categoryMapper.selectById(8L)).thenReturn(category);
        when(articleMapper.selectById(5L)).thenReturn(featured);
        when(articleMapper.selectCount(any())).thenReturn(1L);
        when(articleMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category));
        when(articleTagMapper.selectList(any())).thenReturn(List.of());

        BlogFeedVO result = service.listBlogFeed(8L, 1, 20);

        assertEquals(5L, result.getFeaturedArticle().getId());
        assertEquals(1L, result.getTotal());
        assertEquals(0L, result.getListTotal());
        assertEquals(List.of(), result.getArticles());
    }

    @Test
    void crossCategoryConfigurationFallsBackToLatestArticleInCurrentCategory() {
        Article wrongCategory = article(5L, 9L, 1, "其他分类文章");
        Article latestInCategory = article(6L, 8L, 1, "当前分类最新文章");
        Category category = new Category();
        category.setId(8L);
        category.setCategoryName("人工智能");
        category.setFeaturedArticleId(5L);
        when(categoryMapper.selectById(8L)).thenReturn(category);
        when(articleMapper.selectById(5L)).thenReturn(wrongCategory);
        when(articleMapper.selectOne(any())).thenReturn(latestInCategory);
        when(articleMapper.selectCount(any())).thenReturn(1L);
        when(articleMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category));
        when(articleTagMapper.selectList(any())).thenReturn(List.of());

        BlogFeedVO result = service.listBlogFeed(8L, 1, 20);

        assertEquals(6L, result.getFeaturedArticle().getId());
    }

    @Test
    void clearedConfigurationFallsBackToLatestPublishedArticle() {
        Article latest = article(7L, 1L, 1, "清空配置后的最新文章");
        when(websiteInfoMapper.selectById(1L)).thenReturn(WebsiteInfo.builder().id(1L).build());
        when(articleMapper.selectOne(any())).thenReturn(latest);
        stubFeedQueries(null, 1L);

        BlogFeedVO result = service.listBlogFeed(null, 1, 20);

        assertEquals(7L, result.getFeaturedArticle().getId());
    }

    @Test
    void emptyPublishedScopeReturnsStableEmptyFeed() {
        when(websiteInfoMapper.selectById(1L)).thenReturn(WebsiteInfo.builder().id(1L).build());
        when(articleMapper.selectOne(any())).thenReturn(null);
        when(articleMapper.selectCount(any())).thenReturn(0L);
        when(articleMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));

        BlogFeedVO result = service.listBlogFeed(null, 1, 20);

        assertNull(result.getFeaturedArticle());
        assertEquals(List.of(), result.getArticles());
        assertEquals(0L, result.getTotal());
        assertEquals(0L, result.getListTotal());
    }

    private void stubFeedQueries(Article ordinary, long total) {
        when(articleMapper.selectCount(any())).thenReturn(total);
        when(articleMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            IPage<Article> page = invocation.getArgument(0);
            page.setRecords(ordinary == null ? List.of() : List.of(ordinary));
            return page;
        });
        Category category = new Category();
        category.setId(1L);
        category.setCategoryName("技术");
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category));
        when(articleTagMapper.selectList(any())).thenReturn(List.of());
    }

    private Article article(Long id, Long categoryId, Integer status, String title) {
        return Article.builder()
                .id(id)
                .categoryId(categoryId)
                .status(status)
                .articleTitle(title)
                .articleContent("内容")
                .createTime(new Date())
                .build();
    }
}
