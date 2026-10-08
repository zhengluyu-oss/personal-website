package xyz.kuailemao.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ProductionConfigurationGuardTest {
    private final ProductionConfigurationGuard guard=new ProductionConfigurationGuard();
    @Test void missingSecretFailsBeforeApplicationBeansAndDoesNotPrintValues() {
        var env=new MockEnvironment(); env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.password","SYNTHETIC_PRIVATE_PASSWORD");
        var error=assertThrows(IllegalStateException.class,()->guard.postProcessEnvironment(env,null));
        assertTrue(error.getMessage().contains("spring.security.jwt.key")); assertFalse(error.getMessage().contains("SYNTHETIC_PRIVATE_PASSWORD"));
    }
    @Test void completeExternalProductionConfigurationPassesButUnresolvedPlaceholderDoesNot() {
        var env=new MockEnvironment(); env.setActiveProfiles("prod");
        for(String key:ProductionConfigurationGuard.REQUIRED) env.setProperty(key,"synthetic-configuration-value-for-tests-only");
        env.setProperty("spring.data.redis.host","127.0.0.1");
        assertDoesNotThrow(()->guard.postProcessEnvironment(env,null));
        env.setProperty("spring.mail.password","${PRIVATE_QA_MISSING}");
        var error=assertThrows(IllegalStateException.class,()->guard.postProcessEnvironment(env,null));
        assertFalse(error.getMessage().contains("PRIVATE_QA_MISSING")); assertTrue(error.getMessage().contains("spring.mail.password"));
    }
    @Test void remoteRedisRequiresPassword() {
        var env=new MockEnvironment(); env.setActiveProfiles("prod");
        for(String key:ProductionConfigurationGuard.REQUIRED) env.setProperty(key,"synthetic-configuration-value-for-tests-only");
        env.setProperty("spring.data.redis.host","10.0.0.5");
        var error=assertThrows(IllegalStateException.class,()->guard.postProcessEnvironment(env,null));
        assertTrue(error.getMessage().contains("spring.data.redis.password"));
        env.setProperty("spring.data.redis.password","synthetic-redis-password-for-tests-only");
        assertDoesNotThrow(()->guard.postProcessEnvironment(env,null));
    }
    @Test void productionDefaultIsGuardedAndMixedDevProfileRejected() {
        var env=new MockEnvironment(); env.setDefaultProfiles("prod");
        assertThrows(IllegalStateException.class,()->guard.postProcessEnvironment(env,null));
        env.setActiveProfiles("prod","dev"); assertThrows(IllegalStateException.class,()->guard.postProcessEnvironment(env,null));
        env.setActiveProfiles("dev"); assertDoesNotThrow(()->guard.postProcessEnvironment(env,null));
    }
    @Test void jdbcSchedulerCannotFallBackToMissingPassword() {
        var env=new MockEnvironment(); env.setActiveProfiles("prod");
        for(String key:ProductionConfigurationGuard.REQUIRED) env.setProperty(key,"synthetic-configuration-value-for-tests-only");
        env.setProperty("spring.quartz.job-store-type","jdbc");
        assertThrows(IllegalStateException.class,()->guard.postProcessEnvironment(env,null));
    }
}
