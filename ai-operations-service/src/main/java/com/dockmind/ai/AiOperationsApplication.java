package com.dockmind.ai;

import com.dockmind.common.web.CommonWebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

/** Entry point for the AI Operations Service. */
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(CommonWebConfiguration.class)
public class AiOperationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiOperationsApplication.class, args);
    }
}
