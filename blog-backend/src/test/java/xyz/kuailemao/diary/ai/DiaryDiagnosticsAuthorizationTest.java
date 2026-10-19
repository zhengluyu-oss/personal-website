package xyz.kuailemao.diary.ai;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import xyz.kuailemao.diary.*;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiaryDiagnosticsAuthorizationTest {
    @Test void neitherReadNorDeleteReachesStorageBeforeOwnerAuthentication(){
        for(int status:new int[]{401,403}){
            var service=mock(DiaryService.class);when(service.requireOwner()).thenThrow(new DiaryFailure(status,"受控拒绝"));
            var jdbc=mock(NamedParameterJdbcTemplate.class);var controller=new DiaryDiagnosticsController(service,new DiaryProperties(),jdbc,new DataSourceTransactionManager(mock(DataSource.class)));
            try{assertEquals(status,assertThrows(DiaryFailure.class,()->controller.get("REVIEW",UUID.randomUUID(),true)).status());assertEquals(status,assertThrows(DiaryFailure.class,()->controller.delete("REVIEW",UUID.randomUUID())).status());verifyNoInteractions(jdbc);}finally{controller.close();}
        }
    }
    @Test void cleanupLifecycleDoesNotDependOnAiSettings(){var p=new DiaryProperties();p.setOwnerUserId(1L);var jdbc=mock(NamedParameterJdbcTemplate.class);var controller=new DiaryDiagnosticsController(mock(DiaryService.class),p,jdbc,new DataSourceTransactionManager(mock(DataSource.class)));assertDoesNotThrow(controller::close);}
}
