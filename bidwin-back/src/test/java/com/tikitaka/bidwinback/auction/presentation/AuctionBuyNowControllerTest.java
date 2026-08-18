package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.buynow.BuyNowResult;
import com.tikitaka.bidwinback.auction.application.buynow.BuyNowService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuctionBuyNowControllerTest {

    private static final long MEMBER_ID = 1L;
    private static final long AUCTION_ID = 42L;
    private static final String UP_ENDPOINT =
            "/api/v1/auctions/up/" + AUCTION_ID + "/buy-now";
    private static final String DOWN_ENDPOINT =
            "/api/v1/auctions/down/" + AUCTION_ID + "/buy-now";
    private static final String IDEMPOTENCY_KEY = "buy-now-42-request-1";
    private static final String VALID_REQUEST = """
            {
              "idempotencyKey": "buy-now-42-request-1"
            }
            """;

    @Mock
    private BuyNowService buyNowService;

    @Mock
    private SessionAuthService sessionAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuctionBuyNowController(buyNowService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
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
    void 상향_API는_상향_서비스_메서드를_호출한다() throws Exception {
        // given
        BuyNowResult result = completedResult();
        when(buyNowService.buyUpAuction(
                MEMBER_ID,
                AUCTION_ID,
                IDEMPOTENCY_KEY
        )).thenReturn(result);

        // when & then
        performSuccessfulBuy(UP_ENDPOINT);
        verify(buyNowService).buyUpAuction(
                MEMBER_ID,
                AUCTION_ID,
                IDEMPOTENCY_KEY
        );
    }

    @Test
    void 하향_API는_하향_서비스_메서드를_호출한다() throws Exception {
        // given
        BuyNowResult result = completedResult();
        when(buyNowService.buyDownAuction(
                MEMBER_ID,
                AUCTION_ID,
                IDEMPOTENCY_KEY
        )).thenReturn(result);

        // when & then
        performSuccessfulBuy(DOWN_ENDPOINT);
        verify(buyNowService).buyDownAuction(
                MEMBER_ID,
                AUCTION_ID,
                IDEMPOTENCY_KEY
        );
    }

    private void performSuccessfulBuy(String endpoint) throws Exception {
        mockMvc.perform(post(endpoint)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tradeId").value(7L))
                .andExpect(jsonPath("$.data.auctionId").value(AUCTION_ID))
                .andExpect(jsonPath("$.data.finalPrice").value(232_000L))
                .andExpect(jsonPath("$.data.purchasedAt")
                        .value("2026-07-30T12:34:56"));
    }

    @Test
    void 멱등_키_형식이_올바르지_않으면_400을_응답한다() throws Exception {
        mockMvc.perform(post(UP_ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "invalid key"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"))
                .andExpect(jsonPath("$.error.message")
                        .value("멱등 키 형식이 올바르지 않습니다."));

        verifyNoInteractions(buyNowService);
    }

    @Test
    void 로그인_세션이_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(post(DOWN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_401_2"));

        verifyNoInteractions(buyNowService);
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthConstant.SESSION_KEY,
                AuthMemberFixture.of(MEMBER_ID)
        );
        when(sessionAuthService.isAuthenticatable(MEMBER_ID, 0L))
                .thenReturn(true);
        return session;
    }

    private BuyNowResult completedResult() {
        return new BuyNowResult(
                7L,
                AUCTION_ID,
                232_000L,
                LocalDateTime.of(2026, 7, 30, 12, 34, 56)
        );
    }
}
