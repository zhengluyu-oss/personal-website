package xyz.kuailemao.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.dto.ArticleDTO;
import xyz.kuailemao.domain.entity.Category;
import xyz.kuailemao.mapper.CategoryMapper;
import xyz.kuailemao.mapper.TagMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleServiceImplPublishValidationTest {
    @Test
    void missingCategoryCannotBePublished() {
        ArticleServiceImpl service = spy(new ArticleServiceImpl());
        CategoryMapper categories = mock(CategoryMapper.class);
        ReflectionTestUtils.setField(service, "categoryMapper", categories);

        assertTrue(service.publish(dto()).getMsg().contains("分类不存在"));
        verify(service, never()).saveOrUpdate(any());
    }

    @Test
    void missingTagCannotBePublished() {
        ArticleServiceImpl service = spy(new ArticleServiceImpl());
        CategoryMapper categories = mock(CategoryMapper.class);
        TagMapper tags = mock(TagMapper.class);
        Category category = new Category();
        category.setId(5L);
        when(categories.selectById(5L)).thenReturn(category);
        when(tags.selectBatchIds(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "categoryMapper", categories);
        ReflectionTestUtils.setField(service, "tagMapper", tags);

        assertTrue(service.publish(dto()).getMsg().contains("标签不存在"));
        verify(service, never()).saveOrUpdate(any());
    }

    private ArticleDTO dto() {
        ArticleDTO dto = new ArticleDTO();
        dto.setCategoryId(5L);
        dto.setTagId(List.of(8L));
        return dto;
    }
}
