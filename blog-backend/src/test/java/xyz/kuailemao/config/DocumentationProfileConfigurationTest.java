package xyz.kuailemao.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationProfileConfigurationTest {
    @Test
    void productionDisablesDocumentationAndDevelopmentKeepsExplicitPaths() throws IOException {
        String configuration = resource("/application.yml");
        assertTrue(configuration.contains("on-profile: prod"));
        assertTrue(configuration.contains("api-docs:\n    enabled: false"));
        assertTrue(configuration.contains("swagger-ui:\n    enabled: false"));
        assertTrue(configuration.contains("knife4j:\n  enable: false"));
        assertTrue(configuration.contains("enabled: true\n    path: /v3/api-docs"));
        assertTrue(configuration.contains("enabled: true\n    path: /swagger-ui.html"));
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
