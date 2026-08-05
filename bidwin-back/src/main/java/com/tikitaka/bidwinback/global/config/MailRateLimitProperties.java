package com.tikitaka.bidwinback.global.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.mail.rate-limit")
public record MailRateLimitProperties(
        @NotNull Duration cooldown,
        @NotNull Duration window,
        @Min(1) int maxCount
) {

    public MailRateLimitProperties {
        if (cooldown != null && cooldown.isNegative()) {
            throw new IllegalArgumentException("mail rate limit cooldown must not be negative");
        }
        if (window != null && (window.isZero() || window.isNegative())) {
            throw new IllegalArgumentException("mail rate limit window must be positive");
        }
        if (cooldown != null && window != null && cooldown.compareTo(window) > 0) {
            throw new IllegalArgumentException("mail rate limit cooldown must not exceed window");
        }
    }
}
