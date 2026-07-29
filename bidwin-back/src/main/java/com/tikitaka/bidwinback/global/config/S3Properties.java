package com.tikitaka.bidwinback.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(
        @NotBlank String bucket,
        @NotBlank String region,
        @NotNull Duration presignDuration
) {
}
