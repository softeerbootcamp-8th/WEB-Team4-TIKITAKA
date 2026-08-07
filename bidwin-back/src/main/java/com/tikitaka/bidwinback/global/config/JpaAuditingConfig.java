package com.tikitaka.bidwinback.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

// created_at, last_modified_at을 JVM 기본 시간대가 아닌 서비스 시간대(Asia/Seoul)로 기록해,
// epoch 변환(atZone(Asia/Seoul))과 시간대가 어긋나지 않도록 감사 시각 provider를 고정한다.
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(SERVICE_ZONE));
    }
}
