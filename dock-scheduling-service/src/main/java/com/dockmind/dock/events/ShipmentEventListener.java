package com.dockmind.dock.events;

import com.dockmind.common.web.CorrelationId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Kafka entry point. It only sets up logging context and delegates; retries and dead-lettering
 * are configured in {@link KafkaConsumerConfiguration}.
 */
@Component
public class ShipmentEventListener {

    public static final String TOPIC = "shipment-events";

    private final ShipmentEventHandler handler;

    public ShipmentEventListener(ShipmentEventHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = TOPIC)
    public void onEvent(ConsumerRecord<String, String> record) {
        Header correlation = record.headers().lastHeader("correlationId");
        if (correlation != null) {
            String id = new String(correlation.value(), StandardCharsets.UTF_8);
            if (CorrelationId.isValid(id)) {
                MDC.put(CorrelationId.MDC_KEY, id); // logs for this event carry the original request's ID
            }
        }
        try {
            handler.handle(record.value());
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
