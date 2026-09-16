package xyz.kuailemao.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.dto.ExperienceProjectDTO;
import xyz.kuailemao.domain.entity.WorkExperience;
import xyz.kuailemao.service.impl.ExperienceProjectServiceImpl;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExperienceProjectServiceImplTest {
    @Test void rejectsProjectWhenParentDoesNotExist() {
        WorkExperienceService parents=mock(WorkExperienceService.class);
        when(parents.getOne(any())).thenReturn(null);
        ExperienceProjectServiceImpl service=new ExperienceProjectServiceImpl();
        ReflectionTestUtils.setField(service,"workExperienceService",parents);
        ExperienceProjectDTO dto=new ExperienceProjectDTO(); dto.setProjectName("项目"); dto.setSummary("摘要");
        assertNotEquals(200, service.add(8L,dto).getCode());
    }

    @Test void rejectsReversedProjectDates() {
        WorkExperienceService parents=mock(WorkExperienceService.class);
        when(parents.getOne(any())).thenReturn(WorkExperience.builder().id(8L).isDeleted(0).build());
        ExperienceProjectServiceImpl service=new ExperienceProjectServiceImpl();
        ReflectionTestUtils.setField(service,"workExperienceService",parents);
        ExperienceProjectDTO dto=new ExperienceProjectDTO(); dto.setProjectName("项目"); dto.setSummary("摘要");
        dto.setStartDate(new Date(2_000)); dto.setEndDate(new Date(1_000));
        assertTrue(service.add(8L,dto).getMsg().contains("结束日期"));
    }
}
