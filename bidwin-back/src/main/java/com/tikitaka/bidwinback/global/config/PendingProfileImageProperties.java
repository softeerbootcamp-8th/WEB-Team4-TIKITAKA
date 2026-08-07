package com.tikitaka.bidwinback.global.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.storage.s3.pending-profile-image")
public record PendingProfileImageProperties(
        @NotNull Duration retention,
        @Min(1) @Max(1000) int cleanupBatchSize
) {
}
