package com.tikitaka.bidwinback.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MailRateLimitProperties.class)
public class MailConfig {
}
