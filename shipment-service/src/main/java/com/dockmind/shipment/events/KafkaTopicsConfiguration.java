package com.dockmind.shipment.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics this service owns. Spring creates them on startup if they don't exist.
 * Partitions allow parallel consumers; the shipment-ID key keeps each shipment's events in
 * order within its partition.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaTopicsConfiguration {

    @Bean
    NewTopic shipmentEventsTopic(@Value("${dockmind.kafka.partitions:3}") int partitions,
                                 @Value("${dockmind.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(ShipmentEvents.TOPIC).partitions(partitions).replicas(replicas).build();
    }
}
