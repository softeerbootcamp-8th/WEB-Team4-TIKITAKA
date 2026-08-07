package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.live.AuctionLiveStateService;
import com.tikitaka.bidwinback.auction.infrastructure.sse.AuctionSseMessages;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionSseController {

    private final AuctionLiveStateService stateService;
    private final SseHub sseHub;

    @GetMapping(
            value = "/{auctionId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> subscribeAuction(
            @PathVariable @Positive long auctionId
    ) {
        SseEmitter emitter = sseHub.subscribe(
                List.of(AuctionSseMessages.channel(auctionId)),
                () -> List.of(AuctionSseMessages.state(stateService.getState(auctionId)))
        );
        return streamResponse(emitter);
    }

    @GetMapping(
            value = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> subscribeAuctionList(
            @RequestParam List<@Positive Long> auctionIds
    ) {
        // 채널은 Set으로 정규화되지만 snapshot 공급자는 원본을 그대로 조회하므로,
        // 같은 ID를 반복해 IN 절과 컬렉션을 부풀리지 못하도록 여기서 먼저 중복을 제거한다.
        List<Long> distinctIds = auctionIds.stream().distinct().toList();
        SseEmitter emitter = sseHub.subscribe(
                distinctIds.stream().map(AuctionSseMessages::channel).toList(),
                () -> stateService.getStates(distinctIds).stream()
                        .map(AuctionSseMessages::state)
                        .toList()
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
     * auctionId가 0 이하이거나 잘못된 값이 섞이면 검증·바인딩 예외가 나는데, 공통 핸들러의
     * JSON 본문은 text/event-stream 협상에 실패해 400 대신 406으로 나간다. 여기서 빈 본문의
     * 400으로 변환해 EventSource가 실제 원인(잘못된 요청)을 그대로 받도록 한다.
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
