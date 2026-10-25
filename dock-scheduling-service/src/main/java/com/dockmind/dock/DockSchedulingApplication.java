package com.dockmind.dock;

import com.dockmind.common.web.CommonWebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Entry point for the Dock Scheduling Service. */
@SpringBootApplication
@Import(CommonWebConfiguration.class)
public class DockSchedulingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DockSchedulingApplication.class, args);
    }
}
