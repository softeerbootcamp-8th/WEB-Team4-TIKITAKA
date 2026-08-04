package com.tikitaka.bidwinback.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage.cloudfront")
public record CloudFrontProperties(
        @NotBlank String domain
) {
}
