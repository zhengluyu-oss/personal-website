package xyz.kuailemao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.kuailemao.domain.entity.ExperienceProject;
import xyz.kuailemao.domain.entity.WorkExperience;
import xyz.kuailemao.mapper.ExperienceProjectMapper;
import xyz.kuailemao.mapper.WorkExperienceMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExperienceOverviewServiceTest {
    @BeforeAll
    static void initializeEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WorkExperience.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExperienceProject.class);
    }

    @Test
    void countsPublishedProjectsForEachPublishedExperienceIncludingZero() {
        WorkExperienceMapper experiences = mock(WorkExperienceMapper.class);
        ExperienceProjectMapper projects = mock(ExperienceProjectMapper.class);
        when(experiences.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                WorkExperience.builder().id(9L).build(), WorkExperience.builder().id(4L).build()));
        when(projects.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                ExperienceProject.builder().experienceId(9L).build(),
                ExperienceProject.builder().experienceId(9L).build()));

        var result = new ExperienceOverviewService(experiences, projects).publicCounts();

        assertEquals(2, result.total());
        assertEquals(List.of(2L, 0L), result.projectCounts());
    }

    @Test
    void emptyExperienceListDoesNotQueryProjects() {
        WorkExperienceMapper experiences = mock(WorkExperienceMapper.class);
        ExperienceProjectMapper projects = mock(ExperienceProjectMapper.class);
        when(experiences.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        var result = new ExperienceOverviewService(experiences, projects).publicCounts();

        assertEquals(0, result.total());
        assertEquals(List.of(), result.projectCounts());
        verifyNoInteractions(projects);
    }
}
