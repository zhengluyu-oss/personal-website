package xyz.luyu.knowledge;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeProcessingQueueTest {
    @Test void versionedQueueDeadLettersWithoutRedeclaringLegacyQueue(){
        var config=new KnowledgeQueueConfiguration();
        var active=config.knowledgeParseQueue();
        assertEquals("knowledge.parse.v2",active.getName());
        assertEquals(KnowledgeQueueConfiguration.DEAD_EXCHANGE,active.getArguments().get("x-dead-letter-exchange"));
        assertEquals(KnowledgeQueueConfiguration.DEAD_QUEUE,active.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("knowledge.parse.v1",config.knowledgeLegacyParseQueue().getName());
        assertTrue(config.knowledgeLegacyParseQueue().getArguments().isEmpty());
        assertEquals(KnowledgeQueueConfiguration.DEAD_QUEUE,config.knowledgeDeadQueue().getName());
    }

    @Test void terminalAndPoisonMessagesGoToDeadLetterWhileLegacyDrains() throws Exception {
        var access=mock(KnowledgeAccess.class);
        var store=mock(KnowledgeStore.class);
        var objects=mock(KnowledgePrivateObjects.class);
        var channel=mock(Channel.class);
        when(access.owner()).thenReturn(7L);
        when(access.store()).thenReturn(store);
        var processing=new KnowledgeProcessing(access,objects,mock(RabbitTemplate.class),new KnowledgeProperties());
        UUID id=UUID.randomUUID();
        when(store.jobState(7L,id)).thenReturn(KnowledgeModels.ProcessingState.FAILED);

        assertDoesNotThrow(()->processing.consume(id.toString().getBytes(StandardCharsets.US_ASCII),channel,message(KnowledgeQueueConfiguration.QUEUE,1)));
        verify(channel).basicReject(1,false);
        assertDoesNotThrow(()->processing.consume(id.toString().getBytes(StandardCharsets.US_ASCII),channel,message(KnowledgeQueueConfiguration.LEGACY_QUEUE,2)));
        verify(channel).basicAck(2,false);
        assertDoesNotThrow(()->processing.consume("bad-id".getBytes(StandardCharsets.US_ASCII),channel,message(KnowledgeQueueConfiguration.QUEUE,3)));
        verify(channel).basicReject(3,false);
        verifyNoInteractions(objects);
    }

    private static Message message(String queue,long tag){
        var properties=new MessageProperties();
        properties.setConsumerQueue(queue);
        properties.setDeliveryTag(tag);
        return new Message(new byte[0],properties);
    }
    @Test void brokerCanMigrateExistingQueueAndRouteDeadLetters() throws Exception {
        String uri=System.getenv("KNOWLEDGE_TEST_RABBIT_URI");
        Assumptions.assumeTrue(uri!=null&&!uri.isBlank());
        var factory=new ConnectionFactory();factory.setUri(uri);
        var config=new KnowledgeQueueConfiguration();
        try(var connection=factory.newConnection();var channel=connection.createChannel()){
            channel.queueDeclare(KnowledgeQueueConfiguration.LEGACY_QUEUE,true,false,false,null);
            channel.queueDeclare(KnowledgeQueueConfiguration.LEGACY_QUEUE,true,false,false,null);
            channel.exchangeDeclare(KnowledgeQueueConfiguration.DEAD_EXCHANGE,"direct",true);
            channel.queueDeclare(KnowledgeQueueConfiguration.DEAD_QUEUE,true,false,false,null);
            channel.queueBind(KnowledgeQueueConfiguration.DEAD_QUEUE,KnowledgeQueueConfiguration.DEAD_EXCHANGE,KnowledgeQueueConfiguration.DEAD_QUEUE);
            channel.queueDeclare(KnowledgeQueueConfiguration.QUEUE,true,false,false,config.knowledgeParseQueue().getArguments());
            channel.basicPublish("",KnowledgeQueueConfiguration.QUEUE,null,"invalid".getBytes(StandardCharsets.US_ASCII));
            var delivery=channel.basicGet(KnowledgeQueueConfiguration.QUEUE,false);
            assertNotNull(delivery);
            channel.basicReject(delivery.getEnvelope().getDeliveryTag(),false);
            com.rabbitmq.client.GetResponse dead=null;
            for(int wait=0;wait<20&&dead==null;wait++){
                dead=channel.basicGet(KnowledgeQueueConfiguration.DEAD_QUEUE,true);
                if(dead==null)Thread.sleep(100);
            }
            assertNotNull(dead);
            assertArrayEquals("invalid".getBytes(StandardCharsets.US_ASCII),dead.getBody());
            assertEquals(0,channel.queueDeclarePassive(KnowledgeQueueConfiguration.LEGACY_QUEUE).getMessageCount());
        }
    }
}
