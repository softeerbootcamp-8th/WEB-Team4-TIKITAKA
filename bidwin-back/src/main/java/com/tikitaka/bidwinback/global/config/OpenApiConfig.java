package com.tikitaka.bidwinback.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SESSION_COOKIE_SECURITY_SCHEME = "sessionCookie";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BidWin API")
                        .description("""
                                BidWin 경매 서비스의 HTTP API 문서입니다.

                                모든 JSON 응답은 `success`, `data`, `error` 필드를 갖는 공통 형식입니다.
                                인증 API를 제외한 보호 API는 로그인 시 발급되는 `JSESSIONID` 쿠키가 필요합니다.
                                SSE API는 `text/event-stream`으로 상태 스냅샷과 변경 이벤트를 전달합니다.
                                """)
                        .version("v1")
                )
                .components(new Components().addSecuritySchemes(
                        SESSION_COOKIE_SECURITY_SCHEME,
                        new SecurityScheme()
                                .name("JSESSIONID")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .description("로그인 성공 시 발급되는 세션 쿠키")
                ));
    }
}
