package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.dto.CategoryDTO;
import xyz.kuailemao.domain.entity.Category;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.CategoryVO;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.mapper.CategoryMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplHeroTest {

    @Mock private ArticleMapper articleMapper;
    @Mock private CategoryMapper categoryMapper;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Category.class);
        service = new CategoryServiceImpl();
        ReflectionTestUtils.setField(service, "articleMapper", articleMapper);
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "baseMapper", categoryMapper);
    }

    @Test
    void addingCategoryTrimsAndPersistsPartialHeroCopy() {
        CategoryDTO dto = new CategoryDTO()
                .setCategoryName("AI工具")
                .setHeroEyebrow("  AI · TOOLS  ")
                .setHeroTitleAccent("  与智能协作，  ")
                .setHeroDescription("   ");
        when(categoryMapper.insert(any(Category.class))).thenReturn(1);

        ResponseResult<Void> result = service.addOrUpdateCategory(dto);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        org.mockito.Mockito.verify(categoryMapper).insert(categoryCaptor.capture());
        Category saved = categoryCaptor.getValue();
        assertEquals(200, result.getCode());
        assertEquals("AI · TOOLS", saved.getHeroEyebrow());
        assertEquals("与智能协作，", saved.getHeroTitleAccent());
        assertNull(saved.getHeroDescription());
    }

    @Test
    void updatingCategoryExplicitlyIncludesClearedHeroFields() {
        CategoryDTO dto = new CategoryDTO().setId(8L).setCategoryName("技术笔记");
        when(categoryMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        ResponseResult<Void> result = service.addOrUpdateCategory(dto);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Category>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(categoryMapper).update(isNull(), wrapperCaptor.capture());
        String sqlSet = ((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Category>) wrapperCaptor.getValue()).getSqlSet();
        assertEquals(200, result.getCode());
        assertTrue(sqlSet.contains("hero_eyebrow"));
        assertTrue(sqlSet.contains("hero_title_accent"));
        assertTrue(sqlSet.contains("hero_title"));
        assertTrue(sqlSet.contains("hero_description"));
    }

    @Test
    void oldCategoryWithoutHeroFieldsStillMapsToPublicView() {
        Category category = new Category();
        category.setId(9L);
        category.setCategoryName("旧分类");
        when(categoryMapper.selectById(9L)).thenReturn(category);
        when(articleMapper.selectCount(any())).thenReturn(0L);

        CategoryVO result = service.getCategoryById(9L);

        assertEquals("旧分类", result.getCategoryName());
        assertNull(result.getHeroEyebrow());
        assertNull(result.getHeroTitleAccent());
        assertNull(result.getHeroTitle());
        assertNull(result.getHeroDescription());
    }

    @Test
    void dtoRejectsEveryOverlongHeroField() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        CategoryDTO dto = new CategoryDTO()
                .setCategoryName("校验分类")
                .setHeroEyebrow("x".repeat(61))
                .setHeroTitleAccent("x".repeat(61))
                .setHeroTitle("x".repeat(61))
                .setHeroDescription("x".repeat(241));

        assertEquals(4, validator.validate(dto).size());
    }
}
