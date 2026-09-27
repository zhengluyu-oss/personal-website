package xyz.kuailemao.service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.kuailemao.domain.dto.WebsiteShareDTO;
import xyz.kuailemao.domain.entity.WebsiteShare;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.WebsiteShareVO;

import java.util.List;

public interface WebsiteShareService extends IService<WebsiteShare> {
    List<WebsiteShareVO> listPublic();
    WebsiteShareVO getPublic(Long id);
    Long getLegacyTarget(Long articleId);
    List<WebsiteShareVO> listBack();
    WebsiteShareVO getBack(Long id);
    ResponseResult<Long> saveShare(WebsiteShareDTO dto);
    ResponseResult<Void> deleteShares(List<Long> ids);
}
