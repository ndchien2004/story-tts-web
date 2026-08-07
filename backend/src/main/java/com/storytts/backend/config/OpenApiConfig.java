package com.storytts.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Tài liệu API tại /swagger-ui.html — tiện thử endpoint mà không cần Postman. */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI storyTtsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Story TTS API")
                        .version("v1")
                        .description("REST API cho website đọc & nghe truyện có chức năng Text-to-Speech."))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Dán token nhận được từ POST /api/auth/login")));
    }
}
