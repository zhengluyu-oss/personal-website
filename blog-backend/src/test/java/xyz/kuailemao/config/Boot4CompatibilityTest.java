package xyz.kuailemao.config;

import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.SpringFactoriesLoader;
import tools.jackson.databind.json.JsonMapper;
import xyz.kuailemao.domain.dto.ExperienceProjectDTO;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.domain.ip.BlackListIpInfo;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.WorkExperienceVO;

import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Narrow contexts only: never scans business services or reads private configuration. */
class Boot4CompatibilityTest {
    @Test void jackson3KeepsApiDateAndNullContractsAndHidesAuthenticationEpoch() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    JsonMapper mapper = context.getBean(JsonMapper.class);
                    ExperienceProjectDTO dto = mapper.readValue("{\"startDate\":\"2025-07-17\",\"endDate\":null}", ExperienceProjectDTO.class);
                    WorkExperienceVO result = new WorkExperienceVO(); result.setStartDate(dto.getStartDate());
                    assertEquals("2025-07-17", mapper.readTree(mapper.writeValueAsString(result)).get("startDate").asString());
                    assertFalse(mapper.readTree(mapper.writeValueAsString(ResponseResult.success())).has("data"));
                    LoginUser user = new LoginUser(new User().setUsername("synthetic").setPassword("synthetic-hash"))
                            .setAuthorities(List.of()).setAuthenticationVersion("synthetic-secret-epoch");
                    String json = mapper.writeValueAsString(user);
                    assertFalse(json.contains("authenticationVersion")); assertFalse(json.contains("synthetic-secret-epoch"));
                });
    }

    @Test void databaseJsonHandlerReadsExistingJsonWithoutTypeMetadata() {
        Jackson3TypeHandler handler = new Jackson3TypeHandler(BlackListIpInfo.class);
        BlackListIpInfo info = (BlackListIpInfo) handler.parse("{\"createIp\":\"192.0.2.12\",\"ipDetail\":null}");
        assertEquals("192.0.2.12", info.getCreateIp());
        assertEquals(info, handler.parse(handler.toJson(info)));
    }

    @Test void newBootFactoryKeyRegistersProductionGuardAndDocumentationAssetsRemain() throws Exception {
        var factories = new java.util.Properties();
        try (var input = getClass().getResourceAsStream("/META-INF/spring.factories")) {
            assertNotNull(input); factories.load(input);
        }
        assertEquals(ProductionConfigurationGuard.class.getName(), factories.getProperty(EnvironmentPostProcessor.class.getName()));
        try (var doc = getClass().getResourceAsStream("/META-INF/resources/doc.html")) {
            assertNotNull(doc, "Existing Knife4j documentation entry must remain available");
            assertTrue(new String(doc.readAllBytes(), StandardCharsets.UTF_8).contains("<html"));
        }
    }
}
