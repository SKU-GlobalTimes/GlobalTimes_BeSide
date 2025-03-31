package com.example.globalTimes_be.global.config;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "GlobalTimes API 명세서",
                description = "GlobalTimes 테스트를 위한 API 문서입니다.",
                version = "v1",
                contact = @Contact(
                        name = "globalTimes",
                        url = "https://도메인주소.com"
                )
        ),
        security = @SecurityRequirement(name = "bearerAuth"),
        servers = {
                @Server(url = "http://localhost:8080", description = "로컬 서버"),
                @Server(url = "https://api.도메인주소.com", description = "운영 서버")
        }
)
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("Swagger API") // API 그룹명
                .pathsToMatch("/api/**", "/swagger-ui/**", "/v3/api-docs/**") // 해당 경로만 문서화
                .build();
    }
}
