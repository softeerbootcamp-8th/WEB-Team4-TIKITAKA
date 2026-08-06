package com.tikitaka.bidwinback.global.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

// TEMP: AuctionLiveStateService가 dev에 존재하지 않아 컴파일이 깨져 있었다(SseHeartbeatScheduler
// 참고). 이 테스트도 그 타입을 목으로 썼고, 실제 코드는 아직 stateService.getDatabaseTimeMillis()를
// 호출하지 않는 TODO 상태라 애초에 검증하는 동작 자체가 지금 구현과 안 맞는다. dev에서 관련 기능이
// 정식으로 들어오면 이 클래스를 원래대로 되돌려야 한다.
@Disabled("AuctionLiveStateService 미구현으로 임시 비활성화")
@ExtendWith(MockitoExtension.class)
class SseHeartbeatSchedulerTest {

    @Mock
    private SseHub sseHub;

    private SseHeartbeatScheduler scheduler;

    private static final long SERVER_TIME = 1_754_000_000_000L;

    @BeforeEach
    void setUp() {
        scheduler = new SseHeartbeatScheduler(sseHub);
    }

    @Test
    void 활성_연결이_있으면_모든_연결에_heartbeat를_전송한다() {
        // given
        when(sseHub.hasConnections()).thenReturn(true);
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
        org.mockito.Mockito.doThrow(new IllegalStateException("publish failed"))
                .when(sseHub)
                .broadcast(org.mockito.ArgumentMatchers.any());

        // when & then
        assertThatCode(scheduler::sendHeartbeat).doesNotThrowAnyException();
    }
}
