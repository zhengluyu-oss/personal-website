package xyz.luyu.service;
import com.baomidou.mybatisplus.spring.service.IService;
import xyz.luyu.domain.dto.ExperienceProjectDTO;
import xyz.luyu.domain.entity.ExperienceProject;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.ExperienceProjectVO;
import java.util.List;
public interface ExperienceProjectService extends IService<ExperienceProject> {
    List<ExperienceProjectVO> listPublic(Long experienceId);
    ExperienceProjectVO getPublic(Long experienceId, Long projectId);
    List<ExperienceProjectVO> listBack(Long experienceId);
    ExperienceProjectVO getBack(Long experienceId, Long projectId);
    ResponseResult<Void> add(Long experienceId, ExperienceProjectDTO dto);
    ResponseResult<Void> update(Long experienceId, ExperienceProjectDTO dto);
    ResponseResult<Void> delete(Long experienceId, List<Long> ids);
}
