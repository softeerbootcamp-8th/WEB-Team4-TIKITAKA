package com.tikitaka.bidwinback.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    // 세션 수명처럼 시간에 의존하는 정책을 한 곳에서 고정할 수 있도록 시계를 주입받는다.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
