package xyz.kuailemao.service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.kuailemao.domain.dto.WorkExperienceDTO;
import xyz.kuailemao.domain.entity.WorkExperience;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.WorkExperienceVO;

import java.util.List;

public interface WorkExperienceService extends IService<WorkExperience> {

    List<WorkExperienceVO> listPublic();

    WorkExperienceVO getPublicById(Long id);

    List<WorkExperienceVO> listBack();

    WorkExperienceVO getByIdVO(Long id);

    ResponseResult<Void> addOrUpdate(WorkExperienceDTO dto);

    ResponseResult<Void> deleteByIds(List<Long> ids);
}
