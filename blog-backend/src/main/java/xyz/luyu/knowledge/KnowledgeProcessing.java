package xyz.luyu.knowledge;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
class KnowledgeQueueConfiguration {
    static final String LEGACY_QUEUE="knowledge.parse.v1";
    static final String QUEUE="knowledge.parse.v2";
    static final String DEAD_QUEUE="knowledge.parse.dead.v2";
    static final String DEAD_EXCHANGE="knowledge.parse.dead.exchange.v2";
    @Bean @ConditionalOnProperty(name={"knowledge.enabled","knowledge.processing.enabled"},havingValue="true")
    Queue knowledgeParseQueue(){return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(DEAD_QUEUE).build();}
    // Keep the original queue declared and consumed until its in-flight messages drain.
    @Bean @ConditionalOnProperty(name={"knowledge.enabled","knowledge.processing.enabled"},havingValue="true")
    Queue knowledgeLegacyParseQueue(){return new Queue(LEGACY_QUEUE,true);}
    @Bean @ConditionalOnProperty(name={"knowledge.enabled","knowledge.processing.enabled"},havingValue="true")
    DirectExchange knowledgeDeadExchange(){return new DirectExchange(DEAD_EXCHANGE,true,false);}
    @Bean @ConditionalOnProperty(name={"knowledge.enabled","knowledge.processing.enabled"},havingValue="true")
    Queue knowledgeDeadQueue(){return new Queue(DEAD_QUEUE,true);}
    @Bean @ConditionalOnProperty(name={"knowledge.enabled","knowledge.processing.enabled"},havingValue="true")
    Binding knowledgeDeadBinding(@Qualifier("knowledgeDeadQueue") Queue knowledgeDeadQueue,DirectExchange knowledgeDeadExchange){return BindingBuilder.bind(knowledgeDeadQueue).to(knowledgeDeadExchange).with(DEAD_QUEUE);}
}

/** Only a job identifier crosses RabbitMQ. A confirmed publish can be repeated safely. */
@Service
@ConditionalOnProperty(name={"knowledge.enabled","knowledge.processing.enabled"},havingValue="true")
public final class KnowledgeProcessing {
    private final KnowledgeAccess access;
    private final KnowledgePrivateObjects objects;
    private final RabbitTemplate rabbit;
    private final KnowledgeParseRunner parser;
    private final Semaphore parserSlot=new Semaphore(1);
    KnowledgeProcessing(KnowledgeAccess access,KnowledgePrivateObjects objects,RabbitTemplate rabbit,KnowledgeProperties properties){
        this.access=access;this.objects=objects;this.rabbit=rabbit;this.parser=new KnowledgeParseRunner(objects,properties);
    }
    @Scheduled(fixedDelay=5000)
    public void relay(){
        try{
            if(!objects.ready())return;
            long owner=access.owner();KnowledgeStore store=access.store();
            store.recoverExpiredJobs(owner);
            for(var event:store.pendingOutbox(owner)){
                byte[] payload=event.jobId().toString().getBytes(StandardCharsets.US_ASCII);
                rabbit.execute(channel->{channel.confirmSelect();channel.basicPublish("",KnowledgeQueueConfiguration.QUEUE,new com.rabbitmq.client.AMQP.BasicProperties.Builder().deliveryMode(2).build(),payload);channel.waitForConfirmsOrDie(5000);return null;});
                store.confirmOutbox(owner,event.id());
            }
        }catch(Exception ignored){/* Leave the outbox pending for the next poll; never log content or credentials. */}
    }
    @Scheduled(fixedDelay=3600000,initialDelay=60000)
    public void cleanExpiredObjects(){
        try{
            if(!objects.ready())return;
            long owner=access.owner();KnowledgeStore store=access.store();
            for(var file:store.staleFiles(owner)){
                try{
                    store.markCleanupPending(owner,file.version());
                    objects.delete(file.objectKey());
                    store.markCleaned(owner,file.version());
                }catch(Exception ignored){/* Retry this object in the next pass without blocking other cleanup. */}
            }
            for(var image:store.staleImages(owner)){
                try{
                    if(!store.markImageCleanupPending(owner,image.id()))continue;
                    objects.delete(image.objectKey());
                    store.markImageCleaned(owner,image.id());
                }catch(Exception ignored){/* Keep pending state so the next scan retries deletion. */}
            }
            // An upload can reach OSS but fail before its PostgreSQL claim is committed.
            // Wait a day before reclaiming such objects so an in-flight upload is never deleted.
            for(String key:objects.oldObjectKeys(Instant.now().minus(1,ChronoUnit.DAYS))){
                if(!store.registeredObjectKey(owner,key))try{objects.delete(key);}catch(Exception ignored){}
            }
        }catch(Exception ignored){/* Leave the file pending for the next pass. */}
    }
    @RabbitListener(queues={KnowledgeQueueConfiguration.LEGACY_QUEUE,KnowledgeQueueConfiguration.QUEUE},ackMode="MANUAL",concurrency="1")
    public void consume(byte[] message,Channel channel,Message envelope)throws Exception{
        long delivery=envelope.getMessageProperties().getDeliveryTag();
        boolean deadLetterEnabled=KnowledgeQueueConfiguration.QUEUE.equals(envelope.getMessageProperties().getConsumerQueue());
        UUID id;
        try{id=UUID.fromString(new String(message,StandardCharsets.US_ASCII));}catch(Exception invalid){channel.basicReject(delivery,false);return;}
        if(!parserSlot.tryAcquire()){channel.basicNack(delivery,false,true);return;}
        try{
            long owner=access.owner();KnowledgeStore store=access.store();
            KnowledgeStore.JobTicket ticket=store.claimJob(owner,id);
            if(ticket!=null){
                KnowledgeParseRunner.Outcome result=objects.ready()?parser.run(ticket.objectKey(),ticket.mime(),ticket.size()):
                    new KnowledgeParseRunner.Outcome(java.util.List.of(),new KnowledgeParseRunner.ParseFailure("STORAGE_UNAVAILABLE",true));
                store.finishJob(owner,ticket,result);
            }
            var state=store.jobState(owner,id);
            if(deadLetterEnabled&&(state==KnowledgeModels.ProcessingState.FAILED||state==KnowledgeModels.ProcessingState.NEEDS_OCR))channel.basicReject(delivery,false);
            else channel.basicAck(delivery,false);
        }catch(Exception failure){channel.basicNack(delivery,false,true);Thread.sleep(1000);}
        finally{parserSlot.release();}
    }
}
