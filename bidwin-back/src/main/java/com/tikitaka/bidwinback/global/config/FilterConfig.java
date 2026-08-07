package com.tikitaka.bidwinback.global.config;

import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.AuthExceptionFilter;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.time.Clock;
import java.util.List;

import tools.jackson.databind.ObjectMapper;

@Configuration
public class FilterConfig {
    private static final int SESSION_AUTH_FILTER_ORDER = -100;
    private static final int AUTH_EXCEPTION_FILTER_ORDER = SESSION_AUTH_FILTER_ORDER - 1;
    private static final int CORS_FILTER_ORDER = AUTH_EXCEPTION_FILTER_ORDER - 1;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://bidwin.site"));
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PATCH",
                "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        // 인증 필터가 만든 401 응답도 브라우저가 읽을 수 있도록 가장 먼저 처리한다.
        registration.setOrder(CORS_FILTER_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthExceptionFilter> authExceptionFilter(
            ObjectMapper objectMapper
    ) {
        FilterRegistrationBean<AuthExceptionFilter> registration =
                new FilterRegistrationBean<>(new AuthExceptionFilter(objectMapper));

        registration.addUrlPatterns("/*");
        registration.setOrder(AUTH_EXCEPTION_FILTER_ORDER);

        return registration;
    }

    @Bean
    public FilterRegistrationBean<SessionAuthenticationFilter> sessionAuthenticationFilter(
            SessionAuthService sessionAuthService,
            Clock clock
    ) {
        FilterRegistrationBean<SessionAuthenticationFilter> registration =
                new FilterRegistrationBean<>(
                        new SessionAuthenticationFilter(
                                sessionAuthService,
                                clock
                        )
                );

        registration.addUrlPatterns("/*");
        registration.setOrder(SESSION_AUTH_FILTER_ORDER);

        return registration;
    }
}
