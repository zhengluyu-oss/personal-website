package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kuailemao.domain.dto.WebsiteShareDTO;
import xyz.kuailemao.domain.entity.WebsiteShare;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.WebsiteShareVO;
import xyz.kuailemao.mapper.WebsiteShareMapper;
import xyz.kuailemao.service.WebsiteShareService;

import java.net.URI;
import java.util.List;

@Service
public class WebsiteShareServiceImpl extends ServiceImpl<WebsiteShareMapper, WebsiteShare> implements WebsiteShareService {
    private LambdaQueryWrapper<WebsiteShare> visible() {
        return new LambdaQueryWrapper<WebsiteShare>()
                .eq(WebsiteShare::getIsDeleted, 0)
                .eq(WebsiteShare::getStatus, 1);
    }

    @Override
    public List<WebsiteShareVO> listPublic() {
        return this.list(visible()
                        .select(WebsiteShare::getId, WebsiteShare::getTitle, WebsiteShare::getSiteUrl,
                                WebsiteShare::getSummary, WebsiteShare::getCoverImage,
                                WebsiteShare::getOrderNum, WebsiteShare::getCreateTime)
                        .orderByAsc(WebsiteShare::getOrderNum)
                        .orderByDesc(WebsiteShare::getCreateTime))
                .stream().map(item -> item.asViewObject(WebsiteShareVO.class)).toList();
    }

    @Override
    public WebsiteShareVO getPublic(Long id) {
        WebsiteShare item = this.getOne(visible().eq(WebsiteShare::getId, id));
        return item == null ? null : item.asViewObject(WebsiteShareVO.class);
    }

    @Override
    public Long getLegacyTarget(Long articleId) {
        WebsiteShare item = this.getOne(visible().eq(WebsiteShare::getSourceArticleId, articleId));
        return item == null ? null : item.getId();
    }

    @Override
    public List<WebsiteShareVO> listBack() {
        return this.list(new LambdaQueryWrapper<WebsiteShare>()
                        .select(WebsiteShare::getId, WebsiteShare::getTitle, WebsiteShare::getSiteUrl,
                                WebsiteShare::getSummary, WebsiteShare::getCoverImage,
                                WebsiteShare::getOrderNum, WebsiteShare::getStatus,
                                WebsiteShare::getCreateTime, WebsiteShare::getUpdateTime)
                        .eq(WebsiteShare::getIsDeleted, 0)
                        .orderByAsc(WebsiteShare::getOrderNum)
                        .orderByDesc(WebsiteShare::getCreateTime))
                .stream().map(item -> item.asViewObject(WebsiteShareVO.class)).toList();
    }

    @Override
    public WebsiteShareVO getBack(Long id) {
        WebsiteShare item = this.getOne(new LambdaQueryWrapper<WebsiteShare>()
                .eq(WebsiteShare::getId, id).eq(WebsiteShare::getIsDeleted, 0));
        return item == null ? null : item.asViewObject(WebsiteShareVO.class);
    }

    private boolean validUrl(String value) {
        try {
            URI url = URI.create(value.trim());
            return ("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
                    && url.getHost() != null && url.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Transactional
    @Override
    public ResponseResult<Long> saveShare(WebsiteShareDTO dto) {
        if (!validUrl(dto.getSiteUrl())) return ResponseResult.failure("网站地址须为有效的 HTTP 或 HTTPS 链接");
        int order = dto.getOrderNum() == null ? 1 : dto.getOrderNum();
        int status = dto.getStatus() == null ? 0 : dto.getStatus();
        if (dto.getId() == null) {
            WebsiteShare item = dto.asViewObject(WebsiteShare.class);
            item.setSiteUrl(dto.getSiteUrl().trim());
            item.setOrderNum(order);
            item.setStatus(status);
            item.setIsDeleted(0);
            return this.save(item) ? ResponseResult.success(item.getId()) : ResponseResult.failure();
        }
        WebsiteShare existing = this.getOne(new LambdaQueryWrapper<WebsiteShare>()
                .eq(WebsiteShare::getId, dto.getId()).eq(WebsiteShare::getIsDeleted, 0));
        if (existing == null) return ResponseResult.failure("网站分享不存在");
        boolean updated = this.update(new LambdaUpdateWrapper<WebsiteShare>()
                .eq(WebsiteShare::getId, dto.getId()).eq(WebsiteShare::getIsDeleted, 0)
                .set(WebsiteShare::getTitle, dto.getTitle())
                .set(WebsiteShare::getSiteUrl, dto.getSiteUrl().trim())
                .set(WebsiteShare::getSummary, dto.getSummary())
                .set(WebsiteShare::getCoverImage, dto.getCoverImage())
                .set(WebsiteShare::getContent, dto.getContent())
                .set(WebsiteShare::getSeoTitle, dto.getSeoTitle())
                .set(WebsiteShare::getSeoDescription, dto.getSeoDescription())
                .set(WebsiteShare::getSeoKeywords, dto.getSeoKeywords())
                .set(WebsiteShare::getOrderNum, order)
                .set(WebsiteShare::getStatus, status)
                .set(WebsiteShare::getUpdateTime, new java.util.Date()));
        return updated ? ResponseResult.success(dto.getId()) : ResponseResult.failure();
    }

    @Transactional
    @Override
    public ResponseResult<Void> deleteShares(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return ResponseResult.failure("请选择要删除的内容");
        boolean updated = this.update(new LambdaUpdateWrapper<WebsiteShare>()
                .in(WebsiteShare::getId, ids).eq(WebsiteShare::getIsDeleted, 0)
                .set(WebsiteShare::getIsDeleted, 1)
                .set(WebsiteShare::getUpdateTime, new java.util.Date()));
        return updated ? ResponseResult.success() : ResponseResult.failure();
    }
}
