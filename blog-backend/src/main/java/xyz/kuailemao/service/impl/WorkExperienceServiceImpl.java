package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kuailemao.domain.dto.WorkExperienceDTO;
import xyz.kuailemao.domain.entity.WorkExperience;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.WorkExperienceVO;
import xyz.kuailemao.mapper.WorkExperienceMapper;
import xyz.kuailemao.service.WorkExperienceService;

import java.util.List;

@Service("workExperienceService")
public class WorkExperienceServiceImpl extends ServiceImpl<WorkExperienceMapper, WorkExperience> implements WorkExperienceService {

    @Override
    public List<WorkExperienceVO> listPublic() {
        return this.list(new LambdaQueryWrapper<WorkExperience>()
                        .select(WorkExperience::getId, WorkExperience::getCompany, WorkExperience::getRoleTitle,
                                WorkExperience::getStartDate, WorkExperience::getEndDate, WorkExperience::getIsCurrent,
                                WorkExperience::getHighlights, WorkExperience::getOrderNum, WorkExperience::getStatus,
                                WorkExperience::getCreateTime, WorkExperience::getUpdateTime)
                        .eq(WorkExperience::getIsDeleted, 0)
                        .eq(WorkExperience::getStatus, 1)
                        .orderByAsc(WorkExperience::getOrderNum)
                        .orderByDesc(WorkExperience::getStartDate))
                .stream()
                .map(item -> item.asViewObject(WorkExperienceVO.class))
                .toList();
    }

    @Override
    public WorkExperienceVO getPublicById(Long id) {
        WorkExperience entity = this.getOne(new LambdaQueryWrapper<WorkExperience>()
                .eq(WorkExperience::getId, id)
                .eq(WorkExperience::getIsDeleted, 0)
                .eq(WorkExperience::getStatus, 1));
        return entity == null ? null : entity.asViewObject(WorkExperienceVO.class);
    }

    @Override
    public List<WorkExperienceVO> listBack() {
        return this.list(new LambdaQueryWrapper<WorkExperience>()
                        .eq(WorkExperience::getIsDeleted, 0)
                        .orderByAsc(WorkExperience::getOrderNum)
                        .orderByDesc(WorkExperience::getStartDate))
                .stream()
                .map(item -> item.asViewObject(WorkExperienceVO.class))
                .toList();
    }

    @Override
    public WorkExperienceVO getByIdVO(Long id) {
        WorkExperience entity = this.getOne(new LambdaQueryWrapper<WorkExperience>()
                .eq(WorkExperience::getId, id)
                .eq(WorkExperience::getIsDeleted, 0));
        return entity == null ? null : entity.asViewObject(WorkExperienceVO.class);
    }

    @Transactional
    @Override
    public ResponseResult<Void> addOrUpdate(WorkExperienceDTO dto) {
        if (dto.getIsCurrent() != null && dto.getIsCurrent() == 1) {
            dto.setEndDate(null);
        }
        if (dto.getOrderNum() == null) {
            dto.setOrderNum(1);
        }
        if (dto.getStatus() == null) {
            dto.setStatus(1);
        }
        if (dto.getIsCurrent() == null) {
            dto.setIsCurrent(0);
        }
        WorkExperience entity = dto.asViewObject(WorkExperience.class);
        entity.setIsDeleted(0);
        if (this.saveOrUpdate(entity)) {
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }

    @Transactional
    @Override
    public ResponseResult<Void> deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseResult.failure();
        }
        List<WorkExperience> list = ids.stream().map(id -> WorkExperience.builder().id(id).isDeleted(1).build()).toList();
        if (this.updateBatchById(list)) {
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }
}
