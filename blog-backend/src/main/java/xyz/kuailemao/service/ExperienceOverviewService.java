package xyz.kuailemao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import xyz.kuailemao.domain.entity.ExperienceProject;
import xyz.kuailemao.domain.entity.WorkExperience;
import xyz.kuailemao.mapper.ExperienceProjectMapper;
import xyz.kuailemao.mapper.WorkExperienceMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExperienceOverviewService {
    private final WorkExperienceMapper experiences;
    private final ExperienceProjectMapper projects;

    public ExperienceOverviewService(WorkExperienceMapper experiences, ExperienceProjectMapper projects) {
        this.experiences = experiences;
        this.projects = projects;
    }

    public record Overview(int total, List<Long> projectCounts) {}

    public Overview publicCounts() {
        List<WorkExperience> published = experiences.selectList(new LambdaQueryWrapper<WorkExperience>()
                .select(WorkExperience::getId, WorkExperience::getIsCurrent, WorkExperience::getStartDate, WorkExperience::getOrderNum)
                .eq(WorkExperience::getIsDeleted, 0)
                .eq(WorkExperience::getStatus, 1)
                .orderByDesc(WorkExperience::getIsCurrent)
                .orderByDesc(WorkExperience::getStartDate)
                .orderByAsc(WorkExperience::getOrderNum));
        if (published.isEmpty()) return new Overview(0, List.of());

        List<Long> ids = published.stream().map(WorkExperience::getId).toList();
        Map<Long, Long> counts = projects.selectList(new LambdaQueryWrapper<ExperienceProject>()
                        .select(ExperienceProject::getExperienceId)
                        .in(ExperienceProject::getExperienceId, ids)
                        .eq(ExperienceProject::getIsDeleted, 0)
                        .eq(ExperienceProject::getStatus, 1))
                .stream().collect(Collectors.groupingBy(ExperienceProject::getExperienceId, Collectors.counting()));
        return new Overview(published.size(), ids.stream().map(id -> counts.getOrDefault(id, 0L)).toList());
    }
}
