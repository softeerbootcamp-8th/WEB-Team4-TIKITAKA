package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.BuyNowResult;
import com.tikitaka.bidwinback.auction.application.BuyNowService;
import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthExceptionFilter;
import com.tikitaka.bidwinback.global.auth.AuthMemberFixture;
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
    private static final String ENDPOINT =
            "/api/v1/auctions/" + AUCTION_ID + "/buy-now";
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
    void 로그인한_회원이_즉시구매하면_확정된_거래와_200을_응답한다()
            throws Exception {
        LocalDateTime purchasedAt = LocalDateTime.of(
                2026,
                7,
                30,
                12,
                34,
                56
        );
        BuyNowResult result = new BuyNowResult(
                7L,
                AUCTION_ID,
                232_000L,
                purchasedAt
        );
        when(buyNowService.buy(MEMBER_ID, AUCTION_ID, IDEMPOTENCY_KEY))
                .thenReturn(result);

        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tradeId").value(7L))
                .andExpect(jsonPath("$.data.auctionId").value(AUCTION_ID))
                .andExpect(jsonPath("$.data.finalPrice").value(232_000L))
                .andExpect(jsonPath("$.data.purchasedAt")
                        .value(purchasedAt.toString()));

        verify(buyNowService).buy(MEMBER_ID, AUCTION_ID, IDEMPOTENCY_KEY);
    }

    @Test
    void 멱등_키_형식이_올바르지_않으면_400을_응답한다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
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
        mockMvc.perform(post(ENDPOINT)
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
}
