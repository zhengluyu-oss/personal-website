package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kuailemao.domain.dto.ExperienceProjectDTO;
import xyz.kuailemao.domain.entity.ExperienceProject;
import xyz.kuailemao.domain.entity.WorkExperience;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.ExperienceProjectVO;
import xyz.kuailemao.mapper.ExperienceProjectMapper;
import xyz.kuailemao.service.ExperienceProjectService;
import xyz.kuailemao.service.WorkExperienceService;
import java.util.*;

@Service
public class ExperienceProjectServiceImpl extends ServiceImpl<ExperienceProjectMapper, ExperienceProject> implements ExperienceProjectService {
    @Resource private WorkExperienceService workExperienceService;

    private WorkExperience parent(Long id, boolean publicOnly) {
        return workExperienceService.getOne(new LambdaQueryWrapper<WorkExperience>().eq(WorkExperience::getId,id)
                .eq(WorkExperience::getIsDeleted,0).eq(publicOnly, WorkExperience::getStatus,1));
    }
    private LambdaQueryWrapper<ExperienceProject> owned(Long experienceId) {
        return new LambdaQueryWrapper<ExperienceProject>().eq(ExperienceProject::getExperienceId,experienceId).eq(ExperienceProject::getIsDeleted,0);
    }
    private ExperienceProjectVO vo(ExperienceProject p, WorkExperience parent, boolean content) {
        ExperienceProjectVO vo=p.asViewObject(ExperienceProjectVO.class);
        if(!content) vo.setContent(null);
        vo.setCompany(parent.getCompany()); vo.setCompanyRoleTitle(parent.getRoleTitle()); return vo;
    }
    public List<ExperienceProjectVO> listPublic(Long id) {
        WorkExperience p=parent(id,true); if(p==null) return List.of();
        return list(owned(id).eq(ExperienceProject::getStatus,1).orderByAsc(ExperienceProject::getOrderNum).orderByDesc(ExperienceProject::getId))
                .stream().map(x->vo(x,p,false)).toList();
    }
    public ExperienceProjectVO getPublic(Long id,Long pid) {
        WorkExperience p=parent(id,true); if(p==null) return null;
        ExperienceProject x=getOne(owned(id).eq(ExperienceProject::getId,pid).eq(ExperienceProject::getStatus,1)); return x==null?null:vo(x,p,true);
    }
    public List<ExperienceProjectVO> listBack(Long id) { WorkExperience p=parent(id,false); if(p==null) return List.of(); return list(owned(id).orderByAsc(ExperienceProject::getOrderNum).orderByDesc(ExperienceProject::getId)).stream().map(x->vo(x,p,false)).toList(); }
    public ExperienceProjectVO getBack(Long id,Long pid) { WorkExperience p=parent(id,false); ExperienceProject x=p==null?null:getOne(owned(id).eq(ExperienceProject::getId,pid)); return x==null?null:vo(x,p,true); }
    private String validate(Long id, ExperienceProjectDTO d) { if(parent(id,false)==null)return "工作经历不存在"; if(d.getStartDate()!=null&&d.getEndDate()!=null&&d.getEndDate().before(d.getStartDate()))return "结束日期不能早于开始日期"; return null; }
    @Transactional public ResponseResult<Void> add(Long id,ExperienceProjectDTO d){String e=validate(id,d);if(e!=null)return ResponseResult.failure(e);ExperienceProject x=d.asViewObject(ExperienceProject.class);x.setId(null);x.setExperienceId(id);x.setOrderNum(d.getOrderNum()==null?1:d.getOrderNum());x.setStatus(d.getStatus()==null?0:d.getStatus());x.setIsDeleted(0);return save(x)?ResponseResult.success():ResponseResult.failure();}
    @Transactional public ResponseResult<Void> update(Long id,ExperienceProjectDTO d){if(d.getId()==null)return ResponseResult.failure("项目编号不能为空");String e=validate(id,d);if(e!=null)return ResponseResult.failure(e);ExperienceProject old=getOne(owned(id).eq(ExperienceProject::getId,d.getId()));if(old==null)return ResponseResult.failure("项目不存在或不属于当前经历");ExperienceProject x=d.asViewObject(ExperienceProject.class);x.setExperienceId(id);x.setIsDeleted(0);return updateById(x)?ResponseResult.success():ResponseResult.failure();}
    @Transactional public ResponseResult<Void> delete(Long id,List<Long> ids){if(ids==null||ids.isEmpty())return ResponseResult.failure();long count=count(owned(id).in(ExperienceProject::getId,ids));if(count!=new HashSet<>(ids).size())return ResponseResult.failure("存在不属于当前经历的项目");return updateBatchById(ids.stream().map(x->ExperienceProject.builder().id(x).isDeleted(1).build()).toList())?ResponseResult.success():ResponseResult.failure();}
}
