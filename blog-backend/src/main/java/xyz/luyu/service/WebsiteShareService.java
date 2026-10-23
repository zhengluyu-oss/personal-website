package xyz.luyu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import xyz.luyu.domain.dto.WebsiteShareDTO;
import xyz.luyu.domain.entity.WebsiteShare;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.WebsiteShareVO;

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
