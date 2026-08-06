package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.domain.enums.MailPurpose;
import com.tikitaka.bidwinback.global.config.AsyncConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TokenMailDispatcherTest {

    @Test
    void 메일_전송은_호출_스레드를_차단하지_않는다() throws InterruptedException {
        TokenMailSender tokenMailSender = mock(TokenMailSender.class);
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch sendRelease = new CountDownLatch(1);
        CountDownLatch requestReturned = new CountDownLatch(1);
        AtomicReference<Thread> requestThread = new AtomicReference<>();
        AtomicReference<Thread> sendThread = new AtomicReference<>();

        doAnswer(invocation -> {
            sendThread.set(Thread.currentThread());
            sendStarted.countDown();
            sendRelease.await(5, TimeUnit.SECONDS);
            return null;
        }).when(tokenMailSender).send(
                MailPurpose.EMAIL_VERIFICATION,
                "member@example.com",
                "raw-token"
        );

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.register(AsyncConfig.class);
            context.registerBean(
                    TokenMailDispatcher.class,
                    () -> new TokenMailDispatcher(tokenMailSender)
            );
            context.refresh();

            TokenMailDispatcher dispatcher = context.getBean(TokenMailDispatcher.class);
            Thread caller = new Thread(() -> {
                requestThread.set(Thread.currentThread());
                dispatcher.send(
                        MailPurpose.EMAIL_VERIFICATION,
                        "member@example.com",
                        "raw-token"
                );
                requestReturned.countDown();
            });

            caller.start();
            try {
                assertThat(sendStarted.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(requestReturned.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(sendThread.get()).isNotSameAs(requestThread.get());
            } finally {
                sendRelease.countDown();
                caller.join();
            }
        }
    }
}
