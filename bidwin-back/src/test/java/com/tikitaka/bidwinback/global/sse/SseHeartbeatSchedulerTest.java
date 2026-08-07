package com.tikitaka.bidwinback.global.sse;

import com.tikitaka.bidwinback.auction.application.live.AuctionLiveStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseHeartbeatSchedulerTest {

    @Mock
    private SseHub sseHub;
    @Mock
    private AuctionLiveStateService stateService;

    private SseHeartbeatScheduler scheduler;

    private static final long SERVER_TIME = 1_754_000_000_000L;

    @BeforeEach
    void setUp() {
        scheduler = new SseHeartbeatScheduler(
                sseHub,
                stateService
        );
    }

    @Test
    void 활성_연결이_있으면_모든_연결에_heartbeat를_전송한다() {
        // given
        when(sseHub.hasConnections()).thenReturn(true);
        when(stateService.getDatabaseTimeMillis()).thenReturn(SERVER_TIME);
        ArgumentCaptor<SseMessage<?>> message = ArgumentCaptor.forClass(SseMessage.class);

        // when
        scheduler.sendHeartbeat();

        // then
        verify(sseHub).broadcast(message.capture());
        assertThat(message.getValue().eventName()).isEqualTo("heartbeat");
        assertThat(message.getValue().data()).isEqualTo(SERVER_TIME);
    }

    @Test
    void 활성_연결이_없으면_heartbeat를_만들지_않는다() {
        // given
        when(sseHub.hasConnections()).thenReturn(false);

        // when
        scheduler.sendHeartbeat();

        // then
        verify(sseHub, never()).broadcast(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void heartbeat_전송이_실패해도_다음_스케줄을_위해_예외를_격리한다() {
        // given
        when(sseHub.hasConnections()).thenReturn(true);
        when(stateService.getDatabaseTimeMillis()).thenReturn(SERVER_TIME);
        org.mockito.Mockito.doThrow(new IllegalStateException("publish failed"))
                .when(sseHub)
                .broadcast(org.mockito.ArgumentMatchers.any());

        // when & then
        assertThatCode(scheduler::sendHeartbeat).doesNotThrowAnyException();
    }
}
