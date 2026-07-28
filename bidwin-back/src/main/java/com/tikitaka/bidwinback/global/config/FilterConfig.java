package com.tikitaka.bidwinback.global.config;

import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.AuthExceptionFilter;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import tools.jackson.databind.ObjectMapper;

@Configuration
public class FilterConfig {
    private static final int SESSION_AUTH_FILTER_ORDER = -100;
    private static final int AUTH_EXCEPTION_FILTER_ORDER = SESSION_AUTH_FILTER_ORDER - 1;

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
