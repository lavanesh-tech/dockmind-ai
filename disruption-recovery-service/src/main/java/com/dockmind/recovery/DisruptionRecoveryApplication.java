package com.dockmind.recovery;

import com.dockmind.common.web.CommonWebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

/** Entry point for the Disruption Recovery Service. */
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(CommonWebConfiguration.class)
public class DisruptionRecoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DisruptionRecoveryApplication.class, args);
    }
}
