package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kuailemao.constants.FunctionConst;
import xyz.kuailemao.domain.dto.CategoryDTO;
import xyz.kuailemao.domain.dto.SearchCategoryDTO;
import xyz.kuailemao.domain.entity.Article;
import xyz.kuailemao.domain.entity.Category;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.CategoryVO;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.mapper.CategoryMapper;
import xyz.kuailemao.service.CategoryService;
import xyz.kuailemao.utils.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Date;

/**
 * (Category)表服务实现类
 *
 * @author kuailemao
 * @since 2023-10-15 02:29:14
 */
@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Override
    public List<CategoryVO> listAllCategory() {
        List<Category> categories = this.query().list();

        return categories.stream().map(this::toCategoryVO).toList();
    }

    @Override
    public ResponseResult<Void> addCategory(CategoryDTO categoryDTO) {
        return addOrUpdateCategory(categoryDTO.setId(null));
    }

    @Override
    public List<CategoryVO> searchCategory(SearchCategoryDTO searchCategoryDTO) {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.isNotEmpty(searchCategoryDTO.getCategoryName()), Category::getCategoryName, searchCategoryDTO.getCategoryName());
        if (StringUtils.isNotNull(searchCategoryDTO.getStartTime()) && StringUtils.isNotNull(searchCategoryDTO.getEndTime()))
            queryWrapper.between(Category::getCreateTime, searchCategoryDTO.getStartTime(), searchCategoryDTO.getEndTime());

        return categoryMapper.selectList(queryWrapper).stream().map(this::toCategoryVO).toList();
    }

    @Override
    public CategoryVO getCategoryById(Long id) {
        Category category = categoryMapper.selectById(id);
        return category == null ? null : toCategoryVO(category);
    }

    @Transactional
    @Override
    public ResponseResult<Void> addOrUpdateCategory(CategoryDTO categoryDTO) {
        normalizeHeroCopy(categoryDTO);
        ResponseResult<Void> validation = validateFeaturedArticle(categoryDTO);
        if (validation != null) return validation;
        Category category = categoryDTO.asViewObject(Category.class);
        if (categoryDTO.getId() == null) {
            return categoryMapper.insert(category) > 0 ? ResponseResult.success() : ResponseResult.failure();
        }
        LambdaUpdateWrapper<Category> update = new LambdaUpdateWrapper<Category>()
                .eq(Category::getId, categoryDTO.getId())
                .set(Category::getCategoryName, categoryDTO.getCategoryName())
                .set(Category::getFeaturedArticleId, categoryDTO.getFeaturedArticleId())
                .set(Category::getHeroEyebrow, categoryDTO.getHeroEyebrow())
                .set(Category::getHeroTitleAccent, categoryDTO.getHeroTitleAccent())
                .set(Category::getHeroTitle, categoryDTO.getHeroTitle())
                .set(Category::getHeroDescription, categoryDTO.getHeroDescription())
                .set(Category::getUpdateTime, new Date());
        return categoryMapper.update(null, update) > 0 ? ResponseResult.success() : ResponseResult.failure();
    }

    static void normalizeHeroCopy(CategoryDTO categoryDTO) {
        categoryDTO.setHeroEyebrow(normalizeNullableText(categoryDTO.getHeroEyebrow()));
        categoryDTO.setHeroTitleAccent(normalizeNullableText(categoryDTO.getHeroTitleAccent()));
        categoryDTO.setHeroTitle(normalizeNullableText(categoryDTO.getHeroTitle()));
        categoryDTO.setHeroDescription(normalizeNullableText(categoryDTO.getHeroDescription()));
    }

    private static String normalizeNullableText(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ResponseResult<Void> validateFeaturedArticle(CategoryDTO categoryDTO) {
        if (categoryDTO.getFeaturedArticleId() == null) return null;
        Article article = articleMapper.selectById(categoryDTO.getFeaturedArticleId());
        if (article == null || !Objects.equals(article.getStatus(), xyz.kuailemao.constants.SQLConst.PUBLIC_ARTICLE)) {
            return ResponseResult.failure("分类主推荐只能选择已发布文章");
        }
        if (categoryDTO.getId() == null || !Objects.equals(article.getCategoryId(), categoryDTO.getId())) {
            return ResponseResult.failure("主推荐文章必须属于当前分类");
        }
        return null;
    }

    private CategoryVO toCategoryVO(Category category) {
        return category.asViewObject(CategoryVO.class, item -> {
            item.setArticleCount(articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                    .eq(Article::getCategoryId, category.getId())
                    .eq(Article::getStatus, xyz.kuailemao.constants.SQLConst.PUBLIC_ARTICLE)));
            if (category.getFeaturedArticleId() != null) {
                Article featured = articleMapper.selectById(category.getFeaturedArticleId());
                if (featured != null && Objects.equals(featured.getStatus(), xyz.kuailemao.constants.SQLConst.PUBLIC_ARTICLE)
                        && Objects.equals(featured.getCategoryId(), category.getId())) {
                    item.setFeaturedArticleTitle(featured.getArticleTitle());
                }
            }
        });
    }

    @Transactional
    @Override
    public ResponseResult<Void> deleteCategoryByIds(List<Long> ids) {
        // 是否有剩下文章
        Long count = articleMapper.selectCount(new LambdaQueryWrapper<Article>().in(Article::getCategoryId, ids));
        if (count > 0) return ResponseResult.failure(FunctionConst.CATEGORY_EXIST_ARTICLE);
        // 执行删除
        if (this.removeByIds(ids)) return ResponseResult.success();
        return ResponseResult.failure();
    }
}
