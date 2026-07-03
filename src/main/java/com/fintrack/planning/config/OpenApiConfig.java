package com.fintrack.planning.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI configuration for the Planning Service.
 *
 * <p>Swagger UI: {@code http://localhost:<port>/swagger-ui.html}
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "******";

    @Value("${server.port:8090}")
    private String serverPort;

    @Value("${spring.application.name:planning-service}")
    private String serviceName;

    /**
     * Configures OpenAPI metadata and JWT ****** scheme.
     *
     * @return configured {@link OpenAPI} bean
     */
    @Bean
    public OpenAPI serviceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinTrack – " + serviceName + " API")
                        .description("REST API documentation for the " + serviceName)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("FinTrack Team")
                                .email("dev@fintrack.com"))
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT access token from auth-service")
                        )
                );
    }
}
