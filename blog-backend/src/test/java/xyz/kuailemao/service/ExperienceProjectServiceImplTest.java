package xyz.kuailemao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.dto.ExperienceProjectDTO;
import xyz.kuailemao.domain.entity.ExperienceProject;
import xyz.kuailemao.domain.entity.WorkExperience;
import xyz.kuailemao.domain.vo.ExperienceProjectVO;
import xyz.kuailemao.service.impl.ExperienceProjectServiceImpl;
import java.util.Date;
import java.util.List;
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

    @Test void createsNewProjectsAsDraftByDefault() {
        ExperienceProjectServiceImpl service=serviceWithParent(publicParent());
        ExperienceProjectDTO dto=validDto();
        doReturn(true).when(service).save(any(ExperienceProject.class));

        assertEquals(200, service.add(8L,dto).getCode());
        verify(service).save(argThat(project -> project.getExperienceId().equals(8L)
                && project.getStatus()==0 && project.getOrderNum()==1 && project.getIsDeleted()==0));
    }

    @Test void rejectsInvalidPublicationStatus() {
        ExperienceProjectServiceImpl service=serviceWithParent(publicParent());
        ExperienceProjectDTO dto=validDto(); dto.setStatus(2);
        assertTrue(service.add(8L,dto).getMsg().contains("状态"));
        verify(service,never()).save(any());
    }

    @Test void publicListExcludesBodyFromSummary() {
        ExperienceProjectServiceImpl service=serviceWithParent(publicParent());
        ExperienceProject project=project(12L,8L,1); project.setContent("私有长正文");
        doReturn(List.of(project)).when(service).list(any());

        List<ExperienceProjectVO> result=service.listPublic(8L);
        assertEquals(1,result.size());
        assertNull(result.get(0).getContent());
        assertEquals("示例公司",result.get(0).getCompany());
    }

    @Test void hiddenParentHasNoPublicProjectsButReturnsAfterReenable() {
        WorkExperienceService parents=mock(WorkExperienceService.class);
        when(parents.getOne(any())).thenReturn(null,publicParent());
        ExperienceProjectServiceImpl service=spy(new ExperienceProjectServiceImpl());
        ReflectionTestUtils.setField(service,"workExperienceService",parents);
        doReturn(List.of(project(12L,8L,1))).when(service).list(any());

        assertTrue(service.listPublic(8L).isEmpty());
        assertEquals(1,service.listPublic(8L).size());
    }

    @Test void rejectsProjectFromAnotherParentOnUpdate() {
        ExperienceProjectServiceImpl service=serviceWithParent(publicParent());
        doReturn(null).when(service).getOne(any());
        ExperienceProjectDTO dto=validDto(); dto.setId(12L);

        assertTrue(service.update(8L,dto).getMsg().contains("不属于"));
        verify(service,never()).updateById(any());
    }

    @Test void clearingProjectDatesWritesExplicitNullValues() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExperienceProject.class);
        ExperienceProjectServiceImpl service=serviceWithParent(publicParent());
        doReturn(project(12L,8L,1)).when(service).getOne(any());
        doReturn(true).when(service).updateById(any(ExperienceProject.class));
        doReturn(true).when(service).update(any(Wrapper.class));
        ExperienceProjectDTO dto=validDto(); dto.setId(12L);

        assertEquals(200,service.update(8L,dto).getCode());
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Wrapper<ExperienceProject>> captor=org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(captor.capture());
        LambdaUpdateWrapper<ExperienceProject> update=(LambdaUpdateWrapper<ExperienceProject>)captor.getValue();
        assertTrue(update.getSqlSet().contains("start_date"));
        assertTrue(update.getSqlSet().contains("end_date"));
        assertTrue(update.getParamNameValuePairs().containsValue(null));
    }

    @Test void crossParentBatchDeleteFailsBeforeMutation() {
        ExperienceProjectServiceImpl service=serviceWithParent(publicParent());
        doReturn(1L).when(service).count(any());

        assertTrue(service.delete(8L,List.of(12L,99L)).getMsg().contains("不属于"));
        verify(service,never()).updateBatchById(any());
    }

    private ExperienceProjectServiceImpl serviceWithParent(WorkExperience parent) {
        WorkExperienceService parents=mock(WorkExperienceService.class);
        when(parents.getOne(any())).thenReturn(parent);
        ExperienceProjectServiceImpl service=spy(new ExperienceProjectServiceImpl());
        ReflectionTestUtils.setField(service,"workExperienceService",parents);
        return service;
    }

    private WorkExperience publicParent() {
        return WorkExperience.builder().id(8L).company("示例公司").roleTitle("开发者").status(1).isDeleted(0).build();
    }

    private ExperienceProjectDTO validDto() {
        ExperienceProjectDTO dto=new ExperienceProjectDTO();
        dto.setProjectName("项目"); dto.setSummary("摘要");
        return dto;
    }

    private ExperienceProject project(Long id,Long experienceId,Integer status) {
        return ExperienceProject.builder().id(id).experienceId(experienceId).projectName("项目")
                .summary("摘要").status(status).isDeleted(0).orderNum(1).build();
    }
}
