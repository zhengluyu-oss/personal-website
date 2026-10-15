package xyz.kuailemao.diary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BLOG_DIARY_QA",matches="true")
class DiaryExportLimitsTest {
    @Test void countAndUtf8SizeOverflowRejectInsteadOfTruncating() {
        var p=new DiaryProperties();p.setEnabled(true);p.setOwnerUserId(7L);p.setUrl("jdbc:postgresql://127.0.0.1:15432/diary_qa");p.setPassword("isolated-diary-runtime");
        var config=new DiaryDataConfiguration.Enabled();
        try(var pool=config.diaryDataSource(p)) {
            var store=config.diaryStore(config.diaryJdbcTemplate(pool),config.diaryTransactionManager(pool));
            var admin=new JdbcTemplate(new DriverManagerDataSource(p.getUrl(),"postgres","isolated-diary-admin"));
            long owner=(UUID.randomUUID().getMostSignificantBits() & 0x000fffffffffffffL)+1000;
            admin.update("INSERT INTO diary.entry(id,owner_id,title,body,revision) SELECT gen_random_uuid(),?,'synthetic','x',1 FROM generate_series(1,1001)",owner);
            var now=LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));var filter=new DiaryModels.Search("created",now,now,null,1,20,false);
            assertEquals(400,assertThrows(DiaryFailure.class,()->store.export(owner,new DiaryModels.Export(filter,"json"))).status());
            long sizedOwner=owner+1;
            admin.update("INSERT INTO diary.entry(id,owner_id,title,body,revision) SELECT gen_random_uuid(),?,'synthetic',repeat('中',30000),1 FROM generate_series(1,240)",sizedOwner);
            assertEquals(400,assertThrows(DiaryFailure.class,()->store.export(sizedOwner,new DiaryModels.Export(filter,"json"))).status());
            assertEquals(400,assertThrows(DiaryFailure.class,()->store.export(owner,new DiaryModels.Export(filter,null))).status());
        }
    }
}
