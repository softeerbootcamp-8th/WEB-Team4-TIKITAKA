package com.tikitaka.bidwinback.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AsyncConfigTest {

    @Test
    void 메일_작업이_포화되면_호출_스레드에서_실행한다() throws InterruptedException {
        AsyncConfig asyncConfig = new AsyncConfig();
        ThreadPoolTaskExecutor executor =
                (ThreadPoolTaskExecutor) asyncConfig.mailTaskExecutor();
        CountDownLatch workersStarted = new CountDownLatch(4);
        CountDownLatch workersRelease = new CountDownLatch(1);
        AtomicReference<Thread> overflowTaskThread = new AtomicReference<>();

        executor.initialize();
        try {
            for (int index = 0; index < 4; index++) {
                executor.execute(() -> {
                    workersStarted.countDown();
                    try {
                        workersRelease.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(workersStarted.await(3, TimeUnit.SECONDS)).isTrue();

            for (int index = 0; index < 100; index++) {
                executor.execute(() -> {
                });
            }

            executor.execute(() -> overflowTaskThread.set(Thread.currentThread()));

            assertThat(overflowTaskThread.get()).isSameAs(Thread.currentThread());
        } finally {
            workersRelease.countDown();
            executor.shutdown();
        }
    }

    @Test
    void 비동기_예외를_토큰_노출_없이_기록한다(CapturedOutput output)
            throws NoSuchMethodException {
        RuntimeException failure = new RuntimeException("SMTP failure");

        new AsyncConfig().getAsyncUncaughtExceptionHandler()
                .handleUncaughtException(
                        failure,
                        AsyncConfigTest.class.getDeclaredMethod("mailTask"),
                        "raw-token"
                );

        assertThat(output)
                .contains("Async task failed: mailTask")
                .contains("SMTP failure")
                .doesNotContain("raw-token");
    }

    private void mailTask() {
    }
}
