package xyz.kuailemao.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import xyz.kuailemao.domain.entity.BlackList;
import xyz.kuailemao.domain.ip.BlackListIpInfo;
import xyz.kuailemao.mapper.BlackListMapper;

import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

/** One connection, TEMPORARY synthetic table, fixed loopback QA DB; no persistent schema writes. */
@EnabledIfEnvironmentVariable(named="BLOG_SECURITY_QA_MYSQL", matches="true")
class Boot4DatabaseIntegrationTest {
    @Test void realMybatisBoot4RegistrationPaginationAndJsonRoundTrip() {
        var datasource = new SingleConnectionDataSource(
                "jdbc:mysql://127.0.0.1:13306/blog_security_qa?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                "root", "local-qa-only", true);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(datasource);
            jdbc.execute("CREATE TEMPORARY TABLE t_black_list (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, reason VARCHAR(100), banned_time DATETIME, expires_time DATETIME, type INT, ip_info JSON, create_time DATETIME, update_time DATETIME, is_deleted INT)");
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class))
                    .withUserConfiguration(MybatisPlusConfig.class)
                    .withBean(DataSource.class, () -> datasource)
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        BlackListMapper mapper = context.getBean(BlackListMapper.class);
                        for (int i = 0; i < 3; i++) {
                            BlackList row = BlackList.builder().type(2).reason("synthetic-only")
                                    .ipInfo(BlackListIpInfo.builder().createIp("192.0.2." + (i + 1)).build()).isDeleted(0).build();
                            assertEquals(1, mapper.insert(row));
                        }
                        var page = mapper.selectPage(new Page<BlackList>(1, 2), new LambdaQueryWrapper<BlackList>().orderByAsc(BlackList::getId));
                        assertEquals(3, page.getTotal()); assertEquals(2, page.getRecords().size());
                        assertEquals("192.0.2.1", page.getRecords().get(0).getIpInfo().getCreateIp());
                        BlackList first = page.getRecords().get(0); first.setReason("updated-synthetic");
                        assertEquals(1, mapper.updateById(first));
                        assertEquals("updated-synthetic", mapper.selectById(first.getId()).getReason());
                        assertEquals(1, mapper.deleteById(first.getId()));
                    });
        } finally { datasource.destroy(); }
    }
}
