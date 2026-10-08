package xyz.kuailemao.config.rabbit;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.Set;

/** Preserve existing queue wire format without enabling blanket Java deserialization trust. */
@Configuration
public class RabbitMessageConfiguration {
    @Bean
    public SimpleMessageConverter messageConverter() { return new BoundedQueueConverter(); }

    static final class BoundedQueueConverter extends SimpleMessageConverter {
        private static final int MAX_BYTES = 256 * 1024;
        private static final Set<String> TYPES = Set.of(
                "java.lang.Object", "java.lang.String", "java.lang.Number", "java.lang.Integer", "java.lang.Long", "java.lang.Boolean",
                "java.util.Date", "java.util.HashMap", "java.util.LinkedHashMap", "java.util.Map$Entry",
                "java.util.CollSer", "java.util.ImmutableCollections$Map1", "java.util.ImmutableCollections$MapN",
                "xyz.kuailemao.domain.entity.Log", "xyz.kuailemao.domain.entity.LoginLog");
        BoundedQueueConverter() { setAllowedListPatterns(List.copyOf(TYPES)); }
        @Override public Object fromMessage(Message message) {
            if (message.getBody().length > MAX_BYTES) throw new MessageConversionException("Queue message exceeds safety limit");
            try { return super.fromMessage(message); }
            catch (RuntimeException failure) {
                // Do not attach exceptions which may contain message payloads or verification codes.
                throw new MessageConversionException("Queue message rejected by serialization policy");
            }
        }
        @Override protected ObjectInputStream createObjectInputStream(InputStream input) throws IOException {
            ObjectInputStream stream = super.createObjectInputStream(input);
            stream.setObjectInputFilter(info -> {
                if (info.depth() > 20 || info.references() > 4096 || info.arrayLength() > 4096 || info.streamBytes() > MAX_BYTES)
                    return ObjectInputFilter.Status.REJECTED;
                Class<?> type = info.serialClass();
                if (type == null) return ObjectInputFilter.Status.UNDECIDED;
                while (type.isArray()) type = type.getComponentType();
                return type.isPrimitive() || TYPES.contains(type.getName())
                        ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
            });
            return stream;
        }
    }
}
