package com.dockmind.dock.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * What happens when an event can't be processed.
 *
 * <ul>
 *   <li><b>Transient failure</b> (for example the database is briefly down): retry 3 times with
 *       exponential backoff (200 ms, 400 ms, 800 ms). Bounded, so one bad message can't block
 *       the partition forever.</li>
 *   <li><b>Permanent failure</b> ({@link UnsupportedEventException}): no retries.</li>
 *   <li>Either way, a message that still fails goes to {@value #DLT}, with the error in its
 *       headers, for an operator to inspect and replay. Processing continues with the next
 *       message.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfiguration {

    public static final String DLT = ShipmentEventListener.TOPIC + ".DLT";

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        // Partition -1 lets Kafka choose, so the DLT may have a different partition count.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(DLT, -1));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(200);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(2_000);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(UnsupportedEventException.class);
        return handler;
    }

    @Bean
    NewTopic shipmentEventsTopic(@Value("${dockmind.kafka.partitions:3}") int partitions,
                                 @Value("${dockmind.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(ShipmentEventListener.TOPIC).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    NewTopic shipmentEventsDeadLetterTopic(@Value("${dockmind.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(DLT).partitions(1).replicas(replicas).build();
    }
}
