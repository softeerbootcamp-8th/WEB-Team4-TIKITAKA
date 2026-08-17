package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.live.AuctionBidHistoryCache;
import com.tikitaka.bidwinback.auction.application.live.AuctionLiveState;
import com.tikitaka.bidwinback.auction.application.live.AuctionLiveStateCache;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.infrastructure.sse.AuctionSseMessages;
import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.sse.SseHub;
import com.tikitaka.bidwinback.global.sse.SseMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
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

import java.util.ArrayList;
import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
@Tag(name = "경매 실시간", description = "경매 상태와 입찰 내역 SSE 구독")
public class AuctionSseController {

    private final AuctionLiveStateCache stateCache;
    private final AuctionBidHistoryCache bidHistoryCache;
    private final SseHub sseHub;

    @Operation(
            summary = "경매 상세 실시간 구독",
            description = "연결 직후 `auction-state`를 보내며, 상향 경매는 `bid-history-snapshot`도 보냅니다. 이후 상태 변경과 공개 입찰을 `auction-state`, `bid-created` 이벤트로 전달합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE 연결 성공", content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(responseCode = "400", description = "잘못된 경매 ID", content = @Content),
            @ApiResponse(responseCode = "404", description = "경매를 찾을 수 없음", content = @Content),
            @ApiResponse(responseCode = "503", description = "SSE 연결 한도 초과", content = @Content)
    })
    @GetMapping(
            value = "/{auctionId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> subscribeAuction(
            @Parameter(description = "경매 ID", example = "1")
            @PathVariable @Positive long auctionId
    ) {
        SseEmitter emitter = sseHub.subscribe(
                List.of(AuctionSseMessages.channel(auctionId)),
                () -> initialSingleAuctionState(auctionId)
        );
        return streamResponse(emitter);
    }

    @Operation(
            summary = "경매 목록 실시간 구독",
            description = "요청한 경매들의 현재 상태를 `auction-state` 이벤트로 먼저 보내고, 이후 변경 상태를 같은 이벤트로 전달합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE 연결 성공", content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
            @ApiResponse(responseCode = "400", description = "경매 ID 누락 또는 잘못된 값", content = @Content),
            @ApiResponse(responseCode = "404", description = "경매를 찾을 수 없음", content = @Content),
            @ApiResponse(responseCode = "503", description = "SSE 연결 한도 초과", content = @Content)
    })
    @GetMapping(
            value = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> subscribeAuctionList(
            @Parameter(
                    description = "구독할 경매 ID 목록. `auctionIds=1&auctionIds=2` 형식으로 전달",
                    array = @ArraySchema(schema = @Schema(type = "integer", format = "int64", example = "1"))
            )
            @RequestParam List<@NotNull @Positive Long> auctionIds
    ) {
        // 채널은 Set으로 정규화되지만 snapshot 공급자는 원본을 그대로 조회하므로,
        // 같은 ID를 반복해 IN 절과 컬렉션을 부풀리지 못하도록 여기서 먼저 중복을 제거한다.
        List<Long> distinctIds = auctionIds.stream().distinct().toList();
        SseEmitter emitter = sseHub.subscribe(
                distinctIds.stream().map(AuctionSseMessages::channel).toList(),
                () -> initialMultiAuctionState(distinctIds)
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

    private List<SseMessage<?>> initialMultiAuctionState(List<Long> distinctIds){
        return List.of(AuctionSseMessages.auctionList(
                stateCache.getStates(distinctIds)
        ));
    }

    private List<SseMessage<?>> initialSingleAuctionState(long auctionId) {
        AuctionLiveState state = stateCache.getState(auctionId);
        List<SseMessage<?>> messages = new ArrayList<>();
        messages.add(AuctionSseMessages.state(state));
        if (state.auctionType() == AuctionType.UP) {
            messages.add(AuctionSseMessages.bidHistorySnapshot(
                    auctionId,
                    state.revision(),
                    bidHistoryCache.getHistory(state)
            ));
        }
        return messages;
    }
}
