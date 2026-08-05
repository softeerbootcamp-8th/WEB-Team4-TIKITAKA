package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.live.AuctionLiveStateService;
import com.tikitaka.bidwinback.auction.infrastructure.sse.AuctionSseMessages;
import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.sse.SseHub;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
        SseEmitter emitter = sseHub.subscribe(
                auctionIds.stream().map(AuctionSseMessages::channel).toList(),
                () -> stateService.getStates(auctionIds).stream()
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

    private ResponseEntity<SseEmitter> streamResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}
