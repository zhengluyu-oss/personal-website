package xyz.kuailemao.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import xyz.kuailemao.config.rabbit.RabbitMessageConfiguration;
import xyz.kuailemao.domain.entity.Log;
import xyz.kuailemao.domain.entity.LoginLog;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RabbitMessageCompatibilityTest {
    private final SimpleMessageConverter converter = new RabbitMessageConfiguration().messageConverter();
    private Object roundTrip(Object input) { return converter.fromMessage(converter.toMessage(input, new MessageProperties())); }
    @Test void existingEmailMapsAndAuditRecordsKeepWireCompatibility() {
        Map<String, Object> email = Map.of("type", "adminLogin", "email", "qa@example.invalid", "code", "123456");
        assertEquals(email, roundTrip(email));
        var mutable = new HashMap<String, Object>(email); mutable.put("commentId", 42L); mutable.put("commentType", 1);
        assertEquals(mutable, roundTrip(mutable));
        Log log = Log.builder().userName("synthetic").state(0).createTime(new Date(0)).reqParameter("[REDACTED]").build();
        LoginLog login = LoginLog.builder().userName("synthetic").type(1).createTime(new Date(0)).build();
        assertEquals(log, roundTrip(log)); assertEquals(login, roundTrip(login));
    }
    @Test void unrelatedSerializedClassesAndOversizedBodiesAreRejectedWithoutPayloadInErrors() {
        var error = assertThrows(MessageConversionException.class, () -> roundTrip(new Unexpected("synthetic-secret")));
        assertFalse(error.toString().contains("synthetic-secret")); assertNull(error.getCause());
        assertThrows(MessageConversionException.class, () -> roundTrip("x".repeat(300_000)));
        Object nested = "leaf";
        for (int i = 0; i < 30; i++) nested = new HashMap<>(Map.of("nested", nested));
        Object deep = nested;
        assertThrows(MessageConversionException.class, () -> roundTrip(deep));
    }
    private record Unexpected(String secret) implements Serializable {}
}
