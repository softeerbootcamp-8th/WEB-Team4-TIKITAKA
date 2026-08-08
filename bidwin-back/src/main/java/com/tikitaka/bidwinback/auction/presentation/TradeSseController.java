package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.TradeQueryService;
import com.tikitaka.bidwinback.auction.application.live.TradeLiveStateService;
import com.tikitaka.bidwinback.auction.infrastructure.sse.TradeSseMessages;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.sse.SseHub;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trades")
public class TradeSseController {

    private final TradeQueryService tradeQueryService;
    private final TradeLiveStateService tradeLiveStateService;
    private final SseHub sseHub;

    @GetMapping(
            value = "/{tradeId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> subscribeTrade(
            @Login AuthMember authMember,
            @PathVariable @Positive long tradeId
    ) {
        // 남의 거래 채널을 여는 IDOR를 막기 위해, 연결 자리를 예약하기 전에 참여자인지 먼저 확인한다.
        tradeQueryService.verifyParticipant(authMember.memberId(), tradeId);
        SseEmitter emitter = sseHub.subscribe(
                List.of(TradeSseMessages.channel(tradeId)),
                () -> List.of(TradeSseMessages.state(tradeLiveStateService.getState(tradeId)))
        );
        return streamResponse(emitter);
    }

    /**
     * EventSource는 Accept: text/event-stream만 보내므로 공통 핸들러의 JSON 오류 본문은
     * 협상에 실패해 실제 원인 대신 406이 나간다. 본문 없이 상태 코드만 돌려준다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Void> handleSubscribeFailure(BusinessException exception) {
        return ResponseEntity.status(exception.getErrorCode().getStatus()).build();
    }

    /**
     * tradeId가 0 이하이거나 잘못된 값이면 검증·바인딩 예외가 나는데, 공통 핸들러의 JSON 본문은
     * text/event-stream 협상에 실패해 400 대신 406으로 나간다. 빈 본문의 400으로 변환한다.
     */
    @ExceptionHandler({
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Void> handleInvalidRequest() {
        return ResponseEntity.badRequest().build();
    }

    private ResponseEntity<SseEmitter> streamResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}
