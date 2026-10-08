package xyz.kuailemao.config;

import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import com.aliyun.oss.OSS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.quartz.utils.C3p0PoolingConnectionProvider;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeDependencyCompatibilityTest {
    @Test void redisSerializerKeepsExistingScalarAndMapEncoding() {
        var serializer = new FastJsonRedisSerializer<>(Object.class);
        assertEquals("\"123456\"", new String(serializer.serialize("123456"), StandardCharsets.UTF_8));
        assertEquals("123456", serializer.deserialize("\"123456\"".getBytes(StandardCharsets.UTF_8)));
        assertEquals(42, serializer.deserialize("42".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Map.of("count", 2), serializer.deserialize(serializer.serialize(Map.of("count", 2))));
    }
    @Test void ossClientCanBeConstructedAndClosedWithoutNetworkOperations() {
        OssProperties properties = new OssProperties(); properties.setEndpoint("https://oss.example.invalid");
        properties.setAccessKey("synthetic-key"); properties.setSecretKey("synthetic-secret"); properties.setBucketName("synthetic-bucket");
        OSS client = new OssConfig().ossClient(properties);
        assertNotNull(client); client.shutdown();
    }
    @Test @EnabledIfEnvironmentVariable(named="BLOG_SECURITY_QA_MYSQL", matches="true")
    void quartzC3p0ProviderUsesPatchedPoolWithoutUnsafeCompatibilityFlags() throws Exception {
        C3p0PoolingConnectionProvider provider = new C3p0PoolingConnectionProvider(
                "com.mysql.cj.jdbc.Driver", "jdbc:mysql://127.0.0.1:13306/blog_security_qa?useSSL=false&allowPublicKeyRetrieval=true",
                "root", "local-qa-only", 1, "SELECT 1");
        try {
            provider.initialize();
            try (var connection = provider.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT 1")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
            }
        } finally { provider.shutdown(); }
    }
}
