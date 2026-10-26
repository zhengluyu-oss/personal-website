package xyz.luyu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import xyz.luyu.domain.dto.WorkExperienceDTO;
import xyz.luyu.domain.entity.WorkExperience;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.WorkExperienceVO;

import java.util.List;

public interface WorkExperienceService extends IService<WorkExperience> {

    List<WorkExperienceVO> listPublic();

    WorkExperienceVO getPublicById(Long id);

    List<WorkExperienceVO> listBack();

    WorkExperienceVO getByIdVO(Long id);

    ResponseResult<Void> addOrUpdate(WorkExperienceDTO dto);

    ResponseResult<Void> deleteByIds(List<Long> ids);
}
