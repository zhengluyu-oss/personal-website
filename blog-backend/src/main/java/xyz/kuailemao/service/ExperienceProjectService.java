package xyz.kuailemao.service;
import com.baomidou.mybatisplus.extension.service.IService;
import xyz.kuailemao.domain.dto.ExperienceProjectDTO;
import xyz.kuailemao.domain.entity.ExperienceProject;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.ExperienceProjectVO;
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
