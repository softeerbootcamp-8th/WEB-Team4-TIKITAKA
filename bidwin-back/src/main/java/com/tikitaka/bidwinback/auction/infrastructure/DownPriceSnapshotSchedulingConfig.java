package com.tikitaka.bidwinback.auction.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class DownPriceSnapshotSchedulingConfig {

    public static final String TASK_SCHEDULER = "downPriceSnapshotTaskScheduler";

    @Bean(name = TASK_SCHEDULER)
    public ThreadPoolTaskScheduler downPriceSnapshotTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("down-price-snapshot-");
        return scheduler;
    }
}
