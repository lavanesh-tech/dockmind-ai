package com.dockmind.dock.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(title = "DockMind Dock Scheduling API", version = "v1",
        description = "Register docks and compute validated schedules. All data is scoped to the tenant in your token."))
@SecurityScheme(name = OpenApiConfiguration.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfiguration {

    public static final String BEARER = "bearerAuth";
}
