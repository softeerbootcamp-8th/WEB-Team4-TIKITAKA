package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.BidHistoryService;
import com.tikitaka.bidwinback.auction.application.BidResult;
import com.tikitaka.bidwinback.auction.application.BidService;
import com.tikitaka.bidwinback.auction.domain.enums.BidType;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.exception.BidException;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryItemResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthExceptionFilter;
import com.tikitaka.bidwinback.global.auth.AuthMemberFixture;
import com.tikitaka.bidwinback.global.auth.LoginMemberArgumentResolver;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.BID_PHASE_CHANGED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BID_PRICE_TOO_LOW;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.CONCURRENT_BID_CONFLICT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_BID_UNIT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuctionBidControllerTest {

    private static final long MEMBER_ID = 1L;
    private static final long AUCTION_ID = 42L;
    private static final long BID_ID = 7L;
    private static final long PRICE = 232_000L;
    private static final String ENDPOINT =
            "/api/v1/auctions/up/" + AUCTION_ID + "/bids";
    private static final String VALID_OPEN_REQUEST = """
            {
              "price": 232000,
              "bidType": "OPEN"
            }
            """;
    private static final String VALID_SEALED_REQUEST = """
            {
              "price": 232000,
              "bidType": "SEALED"
            }
            """;

    @Mock
    private BidService bidService;

    @Mock
    private SessionAuthService sessionAuthService;

    @Mock
    private BidHistoryService bidHistoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuctionBidController(
                        bidService,
                        bidHistoryService
                ))
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(
                        new AuthExceptionFilter(new ObjectMapper()),
                        new SessionAuthenticationFilter(
                                sessionAuthService,
                                Clock.systemUTC()
                        )
                )
                .build();
    }

    @Test
    void 로그인한_회원이_입찰하면_생성된_입찰과_201을_응답한다() throws Exception {
        // given
        LocalDateTime bidAt = LocalDateTime.of(2026, 7, 30, 12, 34, 56);
        BidResult result = new BidResult(
                BID_ID,
                AUCTION_ID,
                MEMBER_ID,
                PRICE,
                BidStatus.UP,
                bidAt
        );
        when(bidService.place(MEMBER_ID, AUCTION_ID, PRICE, BidType.OPEN))
                .thenReturn(result);

        // when & then
        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_OPEN_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bidId").value(BID_ID))
                .andExpect(jsonPath("$.data.auctionId").value(AUCTION_ID))
                .andExpect(jsonPath("$.data.bidderId").value(MEMBER_ID))
                .andExpect(jsonPath("$.data.price").value(PRICE))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.bidAt").value(bidAt.toString()));

        verify(bidService).place(MEMBER_ID, AUCTION_ID, PRICE, BidType.OPEN);
    }

    @Test
    void 밀봉입찰_응답에는_제출_가격이_포함되지_않는다() throws Exception {
        LocalDateTime bidAt = LocalDateTime.of(2026, 7, 30, 12, 34, 56);
        BidResult result = new BidResult(
                BID_ID,
                AUCTION_ID,
                MEMBER_ID,
                null,
                BidStatus.SEALED,
                bidAt
        );
        when(bidService.place(MEMBER_ID, AUCTION_ID, PRICE, BidType.SEALED))
                .thenReturn(result);

        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SEALED_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SEALED"))
                .andExpect(jsonPath("$.data.bidAt").value(bidAt.toString()))
                .andExpect(jsonPath("$.data.price").doesNotExist());
    }

    @Test
    void 입찰가가_0_이하면_400을_응답한다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "price": 0,
                                  "bidType": "OPEN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"))
                .andExpect(jsonPath("$.error.message")
                        .value("입찰가는 0보다 커야 합니다."));

        verifyNoInteractions(bidService);
    }

    @Test
    void 입찰가가_천원_단위가_아니면_400을_응답한다() throws Exception {
        when(bidService.place(MEMBER_ID, AUCTION_ID, 232_500L, BidType.OPEN))
                .thenThrow(new BidException(INVALID_BID_UNIT));

        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "price": 232500,
                                  "bidType": "OPEN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUCTION_400_3"));
    }

    @Test
    void 입찰가가_현재가보다_천원_이상_높지_않으면_422를_응답한다() throws Exception {
        when(bidService.place(MEMBER_ID, AUCTION_ID, PRICE, BidType.OPEN))
                .thenThrow(new BidException(BID_PRICE_TOO_LOW));

        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_OPEN_REQUEST))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BID_422_1"));
    }

    @Test
    void 동시_입찰_락_획득에_실패하면_409를_응답한다() throws Exception {
        when(bidService.place(MEMBER_ID, AUCTION_ID, PRICE, BidType.OPEN))
                .thenThrow(new BidException(CONCURRENT_BID_CONFLICT));

        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_OPEN_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BID_409_4"));
    }

    @Test
    void 요청한_입찰_단계와_현재_단계가_다르면_409를_응답한다() throws Exception {
        when(bidService.place(MEMBER_ID, AUCTION_ID, PRICE, BidType.OPEN))
                .thenThrow(new BidException(BID_PHASE_CHANGED));

        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_OPEN_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BID_409_6"));
    }

    @Test
    void 입찰_단계가_없으면_400을_응답한다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "price": 232000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"))
                .andExpect(jsonPath("$.error.message")
                        .value("입찰 유형을 입력해주세요."));

        verifyNoInteractions(bidService);
    }

    @Test
    void 로그인_세션이_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_OPEN_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_401_2"));

        verifyNoInteractions(bidService);
    }

    private MockHttpSession authenticatedSession() {
        return authenticatedSession(MEMBER_ID);
    }

    private MockHttpSession authenticatedSession(long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthConstant.SESSION_KEY,
                AuthMemberFixture.of(memberId)
        );
        when(sessionAuthService.isAuthenticatable(memberId, 0L))
                .thenReturn(true);
        return session;
    }

    @Test
    void 입찰_내역을_조회하면_200과_최신순_목록을_응답한다() throws Exception {
        when(bidHistoryService.getBidHistory(1L, 7L)).thenReturn(
                new BidHistoryResponse(
                        2L,
                        List.of(
                                new BidHistoryItemResponse(
                                        13L,
                                        "나",
                                        210_000L,
                                        1_754_122_920_000L,
                                        true
                                ),
                                new BidHistoryItemResponse(
                                        12L,
                                        "민**켓",
                                        200_000L,
                                        1_754_122_860_000L,
                                        false
                                )
                        )
                )
        );

        mockMvc.perform(get("/api/v1/auctions/{auctionId}/bids", 1L)
                        .session(authenticatedSession(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bidCount").value(2L))
                .andExpect(jsonPath("$.data.bidLog[0].id").value(13L))
                .andExpect(jsonPath("$.data.bidLog[0].bidder").value("나"))
                .andExpect(jsonPath("$.data.bidLog[0].amount").value(210_000L))
                .andExpect(jsonPath("$.data.bidLog[0].biddedAt")
                        .value(1_754_122_920_000L))
                .andExpect(jsonPath("$.data.bidLog[0].isMe").value(true))
                .andExpect(jsonPath("$.data.bidLog[1].bidder").value("민**켓"))
                .andExpect(jsonPath("$.data.bidLog[1].isMe").value(false));

        verify(bidHistoryService).getBidHistory(1L, 7L);
    }

    @Test
    void 입찰_내역이_없으면_빈_목록을_응답한다() throws Exception {
        when(bidHistoryService.getBidHistory(2L, 7L))
                .thenReturn(new BidHistoryResponse(0L, List.of()));

        mockMvc.perform(get("/api/v1/auctions/{auctionId}/bids", 2L)
                        .session(authenticatedSession(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bidCount").value(0L))
                .andExpect(jsonPath("$.data.bidLog").isEmpty());
    }
}
