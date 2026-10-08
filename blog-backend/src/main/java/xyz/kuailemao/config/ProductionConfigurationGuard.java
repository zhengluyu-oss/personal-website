package xyz.kuailemao.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import java.util.*;

/** Runs after external ConfigData loading but before any database, mail or OSS beans start. */
public class ProductionConfigurationGuard implements EnvironmentPostProcessor, Ordered {
    static final List<String> REQUIRED = List.of(
            "spring.security.jwt.key", "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "spring.data.redis.host", "spring.data.redis.password", "spring.rabbitmq.host", "spring.rabbitmq.username",
            "spring.rabbitmq.password", "spring.mail.host", "spring.mail.username", "spring.mail.password",
            "oauth.gitee.client-id", "oauth.gitee.client-secret", "oauth.gitee.redirect-uri",
            "oauth.github.client-id", "oauth.github.client-secret", "oauth.github.redirect-uri",
            "oss.endpoint", "oss.access-key", "oss.secret-key", "oss.bucket-name", "web.index.path");

    @Override public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String[] active = environment.getActiveProfiles();
        List<String> profiles = Arrays.asList(active.length == 0 ? environment.getDefaultProfiles() : active);
        if (!profiles.contains("prod")) return;
        if (profiles.size() != 1) throw new IllegalStateException("Production configuration rejected: prod must be the only profile");
        List<String> invalid = new ArrayList<>();
        for (String name : REQUIRED) {
            String value = read(environment, name);
            if (value == null || value.isBlank() || value.contains("${") || value.contains("CHANGE_ME")
                    || value.contains("REPLACE_ME") || value.contains("[数据库")
                    || (name.equals("spring.security.jwt.key") && value.length() < 32)) invalid.add(name);
        }
        if ("jdbc".equalsIgnoreCase(read(environment,"spring.quartz.job-store-type"))) {
            String key="spring.quartz.properties.org.quartz.dataSource.quartz_jobs.password";
            String value=read(environment,key);
            if(value==null || value.isBlank() || value.contains("${")) invalid.add(key);
        }
        if (!invalid.isEmpty()) throw new IllegalStateException("Missing or invalid production settings: " + String.join(", ",invalid));
    }
    private String read(ConfigurableEnvironment environment, String key) {
        try { return environment.getProperty(key); }
        catch (RuntimeException invalidPlaceholder) { return null; }
    }
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
