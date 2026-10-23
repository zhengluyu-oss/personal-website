package xyz.luyu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import xyz.luyu.constants.SQLConst;
import xyz.luyu.config.OssProperties;
import xyz.luyu.constants.WebsiteInfoConst;
import xyz.luyu.domain.dto.StationmasterInfoDTO;
import xyz.luyu.domain.dto.WebsiteInfoDTO;
import xyz.luyu.domain.entity.Article;
import xyz.luyu.domain.entity.WebsiteInfo;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.WebsiteInfoVO;
import xyz.luyu.enums.UploadEnum;
import xyz.luyu.mapper.ArticleMapper;
import xyz.luyu.mapper.CategoryMapper;
import xyz.luyu.mapper.CommentMapper;
import xyz.luyu.mapper.WebsiteInfoMapper;
import xyz.luyu.service.WebsiteInfoService;
import xyz.luyu.utils.FileUploadUtils;
import xyz.luyu.utils.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * (WebsiteInfo)表服务实现类
 *
 * @author luyu
 * @since 2023-12-27 14:07:34
 */
@Slf4j
@Service("websiteInfoService")
public class WebsiteInfoServiceImpl extends ServiceImpl<WebsiteInfoMapper, WebsiteInfo> implements WebsiteInfoService {

    @Resource
    private FileUploadUtils fileUploadUtils;

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private OssProperties ossProperties;

    @Transactional
    @Override
    public ResponseResult<String> uploadImageInsertOrUpdate(UploadEnum uploadEnum, MultipartFile avatar, Integer type) {
        if (!(Integer.valueOf(0).equals(type) && uploadEnum == UploadEnum.WEBSITE_INFO_AVATAR)
                && !(Integer.valueOf(1).equals(type) && uploadEnum == UploadEnum.WEBSITE_INFO_BACKGROUND)) {
            return ResponseResult.failure("图片用途无效");
        }
        WebsiteInfo before = this.getById(WebsiteInfoConst.WEBSITE_INFO_ID);
        if (before == null) return ResponseResult.failure("网站信息不存在");
        String oldUrl = type == 0 ? before.getWebmasterAvatar() : before.getWebmasterProfileBackground();
        final String url;
        try {
            url = fileUploadUtils.upload(uploadEnum, avatar);
            if (url == null || url.isBlank()) return ResponseResult.failure("图片上传失败");
        } catch (Exception e) {
            log.warn("新站长图片上传失败，保留原图 ({})", e.getClass().getSimpleName());
            return ResponseResult.failure("图片校验或上传失败，原图已保留");
        }
        boolean managed = TransactionSynchronizationManager.isSynchronizationActive();
        AtomicBoolean switched = new AtomicBoolean();
        if (managed) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                cleanupUnreferencedImage(status == STATUS_COMMITTED && switched.get() ? oldUrl : url, uploadEnum);
            }
        });
        try {
            LambdaUpdateWrapper<WebsiteInfo> update = new LambdaUpdateWrapper<WebsiteInfo>()
                    .eq(WebsiteInfo::getId, WebsiteInfoConst.WEBSITE_INFO_ID);
            if (type == 0) {
                update.set(WebsiteInfo::getWebmasterAvatar, url);
                if (oldUrl == null) update.isNull(WebsiteInfo::getWebmasterAvatar);
                else update.eq(WebsiteInfo::getWebmasterAvatar, oldUrl);
            } else {
                update.set(WebsiteInfo::getWebmasterProfileBackground, url);
                if (oldUrl == null) update.isNull(WebsiteInfo::getWebmasterProfileBackground);
                else update.eq(WebsiteInfo::getWebmasterProfileBackground, oldUrl);
            }
            if (baseMapper.update(null, update) != 1) {
                if (!managed) cleanupUnreferencedImage(url, uploadEnum);
                return ResponseResult.failure("图片配置已变化，请刷新后重试，原图已保留");
            }
            switched.set(true);
            if (!managed) cleanupUnreferencedImage(oldUrl, uploadEnum);
            return ResponseResult.success(url);
        } catch (RuntimeException databaseFailure) {
            if (!managed) cleanupUnreferencedImage(url, uploadEnum);
            throw databaseFailure; // Let the enclosing transaction roll back before cleanup.
        }
    }

    private void cleanupUnreferencedImage(String url, UploadEnum purpose) {
        if (url == null || url.isBlank()) return;
        String key;
        try { key = fileUploadUtils.requireObjectKey(url, purpose.getDir()); }
        catch (RuntimeException legacyUrl) { log.warn("历史站长图片未自动清理，用途: {}", purpose.name()); return; }
        try {
            WebsiteInfo current = this.getById(WebsiteInfoConst.WEBSITE_INFO_ID);
            if (current == null) return; // Missing reference state cannot authorize cleanup.
            for (String reference : new String[]{current.getWebmasterAvatar(), current.getWebmasterProfileBackground()}) {
                if (reference == null || reference.isBlank()) continue;
                try {
                    if (key.equals(fileUploadUtils.referenceObjectKey(reference, purpose.getDir()))) return;
                } catch (IllegalArgumentException otherPurpose) { /* Different object purpose or a legacy external image. */ }
            }
            if (!fileUploadUtils.deleteTrustedFile(url, purpose.getDir())) log.warn("站长旧图片待重试清理: {}", key);
        } catch (RuntimeException failed) {
            log.warn("站长图片清理待重试: {} ({})", key, failed.getClass().getSimpleName());
        }
    }

    @Override
    public WebsiteInfoVO selectWebsiteInfo() {
        WebsiteInfo current = this.getById(WebsiteInfoConst.WEBSITE_INFO_ID);
        WebsiteInfoVO websiteInfoVO = current == null ? null : current.asViewObject(WebsiteInfoVO.class);
        // 运行时长
        if (StringUtils.isNotNull(websiteInfoVO)) {
            if (current.getHeroVideoKey() != null
                    && !current.getHeroVideoKey().isBlank()
                    && ossProperties.getMediaDomain() != null
                    && ossProperties.getMediaDomain().startsWith("https://")) {
                websiteInfoVO.setHeroVideoUrl(ossProperties.getMediaDomain().replaceAll("/+$", "")
                        + "/" + current.getHeroVideoKey());
            }
            if (websiteInfoVO.getBlogFeaturedArticleId() != null) {
                Article featured = articleMapper.selectById(websiteInfoVO.getBlogFeaturedArticleId());
                if (featured != null && Objects.equals(featured.getStatus(), SQLConst.PUBLIC_ARTICLE)) {
                    websiteInfoVO.setBlogFeaturedArticleTitle(featured.getArticleTitle());
                }
            }
            if (articleMapper.selectCount(null) <= 0)  return websiteInfoVO;
            LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(Article::getUpdateTime).orderByDesc(Article::getUpdateTime).last(SQLConst.LIMIT_ONE_SQL);
            websiteInfoVO.setLastUpdateTime(articleMapper.selectOne(wrapper).getUpdateTime());
            websiteInfoVO.setArticleCount(articleMapper.selectCount(null));
            List<String> listArticleContent = articleMapper.selectList(null).stream().map(Article::getArticleContent).toList();
            // 合成一个string
            String mergedString = String.join("", listArticleContent);
            websiteInfoVO.setWordCount((long) extractTextFromMarkdown(mergedString).length());
            wrapper.clear();
            wrapper.select(Article::getVisitCount);
            websiteInfoVO.setVisitCount(articleMapper.selectObjs(wrapper).stream().mapToLong(visitCount -> (Long) visitCount).sum());
            websiteInfoVO.setCategoryCount(categoryMapper.selectCount(null));
            websiteInfoVO.setCommentCount(commentMapper.selectCount(null));
            return websiteInfoVO;
        }
        return null;
    }

    @Transactional
    @Override
    public ResponseResult<Void> updateStationmasterInfo(StationmasterInfoDTO stationmasterInfoDTO) {
        WebsiteInfo websiteInfo = stationmasterInfoDTO.asViewObject(WebsiteInfo.class, v -> v.setId(WebsiteInfoConst.WEBSITE_INFO_ID));
        if (StringUtils.isNotNull(websiteInfo)) {
            this.saveOrUpdate(websiteInfo);
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }

    @Transactional
    @Override
    public ResponseResult<Void> updateWebsiteInfo(WebsiteInfoDTO websiteInfoDTO) {
        if (websiteInfoDTO.getBlogFeaturedArticleId() != null) {
            Article featured = articleMapper.selectById(websiteInfoDTO.getBlogFeaturedArticleId());
            if (featured == null || !Objects.equals(featured.getStatus(), SQLConst.PUBLIC_ARTICLE)) {
                return ResponseResult.failure("聚合页主推荐只能选择已发布文章");
            }
        }
        WebsiteInfo websiteInfo = websiteInfoDTO.asViewObject(WebsiteInfo.class, v -> v.setId(WebsiteInfoConst.WEBSITE_INFO_ID));
        if (StringUtils.isNotNull(websiteInfo)) {
            this.saveOrUpdate(websiteInfo);
            this.update(new LambdaUpdateWrapper<WebsiteInfo>()
                    .eq(WebsiteInfo::getId, WebsiteInfoConst.WEBSITE_INFO_ID)
                    .set(WebsiteInfo::getBlogFeaturedArticleId, websiteInfoDTO.getBlogFeaturedArticleId()));
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }

    /**
     * 从Markdown文档中提取文字内容
     *
     * @param markdownContent Markdown文档内容
     * @return 文字内容
     */
    private static String extractTextFromMarkdown(String markdownContent) {
        // 使用正则表达式匹配Markdown文档中的文字内容，并去掉空格
        Pattern pattern = Pattern.compile("[^#>\\*\\[\\]`\\s]+");
        Matcher matcher = pattern.matcher(markdownContent);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            result.append(matcher.group()).append("\n");
        }

        return result.toString().trim();
    }
}
