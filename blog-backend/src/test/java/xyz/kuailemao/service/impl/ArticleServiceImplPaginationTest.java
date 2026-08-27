package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.dto.SearchArticleDTO;
import xyz.kuailemao.domain.entity.Article;
import xyz.kuailemao.domain.entity.ArticleTag;
import xyz.kuailemao.domain.entity.Category;
import xyz.kuailemao.domain.entity.Tag;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.domain.vo.ArticleListVO;
import xyz.kuailemao.domain.vo.PageVO;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.mapper.ArticleTagMapper;
import xyz.kuailemao.mapper.CategoryMapper;
import xyz.kuailemao.mapper.TagMapper;
import xyz.kuailemao.mapper.UserMapper;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceImplPaginationTest {

    @Mock private ArticleMapper articleMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private ArticleTagMapper articleTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private UserMapper userMapper;

    private ArticleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ArticleServiceImpl();
        ReflectionTestUtils.setField(service, "articleMapper", articleMapper);
        ReflectionTestUtils.setField(service, "baseMapper", articleMapper);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "articleTagMapper", articleTagMapper);
        ReflectionTestUtils.setField(service, "tagMapper", tagMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
    }

    @Test
    void paginatesFilteredAdminResultsAndEnrichesThemInBatches() {
        Article first = article(12L, "第二篇");
        Article second = article(11L, "第一篇");
        when(articleMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            IPage<Article> page = invocation.getArgument(0);
            page.setRecords(List.of(first, second));
            page.setTotal(12L);
            return page;
        });
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category()));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(User.builder().id(7L).username("Admin").build()));
        when(articleTagMapper.selectList(any())).thenReturn(List.of(
                ArticleTag.builder().articleId(12L).tagId(3L).build(),
                ArticleTag.builder().articleId(11L).tagId(3L).build()));
        Tag tag = new Tag();
        tag.setId(3L);
        tag.setTagName("分页");
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag));

        SearchArticleDTO search = new SearchArticleDTO();
        search.setArticleTitle("篇");
        search.setCategoryId(2L);
        search.setStatus(1);
        search.setIsTop(0);
        search.setPageNum(2);
        search.setPageSize(10);

        PageVO<List<ArticleListVO>> result = service.searchArticle(search);

        assertEquals(12L, result.getTotal());
        assertEquals(List.of(12L, 11L), result.getPage().stream().map(ArticleListVO::getId).toList());
        assertTrue(result.getPage().stream().allMatch(item -> "技术".equals(item.getCategoryName())));
        assertTrue(result.getPage().stream().allMatch(item -> "Admin".equals(item.getUserName())));
        assertTrue(result.getPage().stream().allMatch(item -> List.of("分页").equals(item.getTagsName())));
        verify(categoryMapper, times(1)).selectBatchIds(any());
        verify(userMapper, times(1)).selectBatchIds(any());
        verify(articleTagMapper, times(1)).selectList(any());
        verify(tagMapper, times(1)).selectBatchIds(any());
    }

    @Test
    void returnsStableEmptyPage() {
        when(articleMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));

        PageVO<List<ArticleListVO>> result = service.listArticle(4, 10);

        assertEquals(0L, result.getTotal());
        assertEquals(List.of(), result.getPage());
    }

    private Article article(Long id, String title) {
        return Article.builder()
                .id(id)
                .categoryId(2L)
                .userId(7L)
                .articleTitle(title)
                .createTime(new Date())
                .build();
    }

    private Category category() {
        Category category = new Category();
        category.setId(2L);
        category.setCategoryName("技术");
        return category;
    }
}
