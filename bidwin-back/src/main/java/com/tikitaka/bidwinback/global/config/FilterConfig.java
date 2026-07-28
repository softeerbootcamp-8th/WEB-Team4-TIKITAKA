package com.tikitaka.bidwinback.global.config;

import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import tools.jackson.databind.ObjectMapper;

@Configuration
public class FilterConfig {
    private static final int SESSION_AUTH_FILTER_ORDER = -100;

    @Bean
    public FilterRegistrationBean<SessionAuthenticationFilter> sessionAuthenticationFilter(
            ObjectMapper objectMapper,
            SessionAuthService sessionAuthService,
            Clock clock
    ) {
        FilterRegistrationBean<SessionAuthenticationFilter> registration =
                new FilterRegistrationBean<>(
                        new SessionAuthenticationFilter(
                                objectMapper,
                                sessionAuthService,
                                clock
                        )
                );

        registration.addUrlPatterns("/*");
        registration.setOrder(SESSION_AUTH_FILTER_ORDER);

        return registration;
    }
}
