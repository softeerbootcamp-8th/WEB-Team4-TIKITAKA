package com.tikitaka.bidwinback.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuctionSearchProperties.class)
public class AuctionSearchConfig {
}
