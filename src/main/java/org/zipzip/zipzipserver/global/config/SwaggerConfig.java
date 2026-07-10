package org.zipzip.zipzipserver.global.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Apple 로그인으로 발급받은 Access Token")
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Zipzip Server API")
                                .description("Zipzip Server API 문서")
                                .version("v1.0.0"))
                // 엔드포인트마다 @SecurityRequirement를 일일이 붙이지 않아도, Swagger UI의
                // Authorize에 넣은 토큰이 기본적으로 모든 요청에 실리도록 전역 기본값을 건다.
                // 실제 인증이 필요 없는 공개 엔드포인트(Apple 로그인 등)는 해당 컨트롤러에서
                // security = {}로 개별적으로 걷어낸다.
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
