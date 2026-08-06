package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.TradeConfirmationResult;
import com.tikitaka.bidwinback.auction.application.TradeConfirmationService;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TradeConfirmationControllerTest {

    private static final long MEMBER_ID = 1L;
    private static final long TRADE_ID = 7L;
    private static final long AUCTION_ID = 42L;
    private static final long FINAL_PRICE = 200_000L;
    private static final String BUYER_ENDPOINT =
            "/api/v1/trades/" + TRADE_ID + "/buyer-confirmation";
    private static final String SELLER_ENDPOINT =
            "/api/v1/trades/" + TRADE_ID + "/seller-confirmation";

    @Mock
    private TradeConfirmationService tradeConfirmationService;

    @Mock
    private SessionAuthService sessionAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TradeConfirmationController(
                        tradeConfirmationService
                ))
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
    void 구매자_확정_API는_예치_완료_상태를_응답한다() throws Exception {
        when(tradeConfirmationService.confirmBuyer(MEMBER_ID, TRADE_ID))
                .thenReturn(result(TradeStatus.CONFIRMED));

        mockMvc.perform(post(BUYER_ENDPOINT).session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tradeId").value(TRADE_ID))
                .andExpect(jsonPath("$.data.auctionId").value(AUCTION_ID))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.finalPrice").value(FINAL_PRICE));

        verify(tradeConfirmationService).confirmBuyer(MEMBER_ID, TRADE_ID);
    }

    @Test
    void 판매자_확정_API는_대금_이동_완료_상태를_응답한다() throws Exception {
        when(tradeConfirmationService.confirmSeller(MEMBER_ID, TRADE_ID))
                .thenReturn(result(TradeStatus.COMPLETED));

        mockMvc.perform(post(SELLER_ENDPOINT).session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        verify(tradeConfirmationService).confirmSeller(MEMBER_ID, TRADE_ID);
    }

    @Test
    void 로그인_세션이_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(post(BUYER_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_401_2"));

        verifyNoInteractions(tradeConfirmationService);
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

    private TradeConfirmationResult result(TradeStatus status) {
        return new TradeConfirmationResult(
                TRADE_ID,
                AUCTION_ID,
                status,
                FINAL_PRICE
        );
    }
}
