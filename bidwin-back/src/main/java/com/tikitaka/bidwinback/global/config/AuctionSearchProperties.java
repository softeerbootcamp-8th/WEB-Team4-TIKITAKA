package com.tikitaka.bidwinback.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auction.search")
public record AuctionSearchProperties(
        boolean fulltextEnabled
) {
}
