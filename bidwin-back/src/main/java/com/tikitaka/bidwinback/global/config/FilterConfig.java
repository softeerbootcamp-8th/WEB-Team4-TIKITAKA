package com.tikitaka.bidwinback.global.config;

import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;

@Configuration
public class FilterConfig {
    private static final int SESSION_AUTH_FILTER_ORDER = -100;

    @Bean
    public FilterRegistrationBean<SessionAuthenticationFilter> sessionAuthenticationFilter(
            ObjectMapper objectMapper
    ) {
        FilterRegistrationBean<SessionAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new SessionAuthenticationFilter(objectMapper));

        registration.addUrlPatterns("/*");
        registration.setOrder(SESSION_AUTH_FILTER_ORDER);

        return registration;
    }
}
