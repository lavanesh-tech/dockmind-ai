package com.dockmind.shipment;

import com.dockmind.common.web.CommonWebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Shipment Service.
 *
 * <p>{@code @SpringBootApplication} turns on component scanning for this package and
 * below, plus Spring Boot auto-configuration. Shared web infrastructure lives in another
 * package, so it is imported explicitly rather than picked up by scanning.
 */
@SpringBootApplication
@Import(CommonWebConfiguration.class)
@EnableScheduling // runs the outbox relay
public class ShipmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipmentServiceApplication.class, args);
    }
}
