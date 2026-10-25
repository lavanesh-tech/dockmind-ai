package com.dockmind.shipment.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. The full document is generated from the controllers at
 * {@code /v3/api-docs}, with an interactive UI at {@code /swagger-ui.html}.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "DockMind Shipment API",
        version = "v1",
        description = "Book shipments and record truck arrivals. All data is scoped to the tenant in your access token."))
@SecurityScheme(name = OpenApiConfiguration.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfiguration {

    public static final String BEARER = "bearerAuth";
}
