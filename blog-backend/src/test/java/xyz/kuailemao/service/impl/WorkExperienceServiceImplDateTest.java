package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xyz.kuailemao.domain.dto.WorkExperienceDTO;
import xyz.kuailemao.domain.entity.WorkExperience;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class WorkExperienceServiceImplDateTest {
    @Test
    void clearingCompanyEndDateWritesExplicitNull() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WorkExperience.class);
        WorkExperienceServiceImpl service = spy(new WorkExperienceServiceImpl());
        doReturn(true).when(service).saveOrUpdate(any(WorkExperience.class));
        doReturn(true).when(service).update(any(Wrapper.class));
        WorkExperienceDTO dto = new WorkExperienceDTO().setCompany("公司").setRoleTitle("开发")
                .setStartDate(new Date()).setId(5L).setIsCurrent(1);

        assertEquals(200, service.addOrUpdate(dto).getCode());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<WorkExperience>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(captor.capture());
        LambdaUpdateWrapper<WorkExperience> update = (LambdaUpdateWrapper<WorkExperience>) captor.getValue();
        assertTrue(update.getSqlSet().contains("end_date"));
        assertTrue(update.getParamNameValuePairs().containsValue(null));
    }
}
