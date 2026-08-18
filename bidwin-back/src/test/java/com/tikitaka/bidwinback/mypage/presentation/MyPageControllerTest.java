package com.tikitaka.bidwinback.mypage.presentation;

import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthExceptionFilter;
import com.tikitaka.bidwinback.global.auth.AuthMemberFixture;
import com.tikitaka.bidwinback.global.auth.LoginMemberArgumentResolver;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import com.tikitaka.bidwinback.mypage.application.MyPageService;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.ActiveTradeResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.BuyingItemResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.DepositResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyPageResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.ProfileResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.SellingItemResponse;
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
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MyPageControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String ENDPOINT = "/api/v1/mypage";

    @Mock
    private MyPageService myPageService;

    @Mock
    private SessionAuthService sessionAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MyPageController(myPageService))
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
    void 로그인한_회원이_마이페이지를_조회하면_200과_각_블록을_응답한다() throws Exception {
        when(myPageService.getMyPage(MEMBER_ID)).thenReturn(new MyPageResponse(
                new ProfileResponse("급처하는근성", "https://cdn/p.png", 1_752_886_800_000L, 17L, 20L),
                new DepositResponse(60_000L, 16_000L),
                List.of(new ActiveTradeResponse(
                        1L, 90L, "닌텐도 스위치", null,
                        "BUYER", "PAYMENT_PENDING", 265_000L
                )),
                List.of(new SellingItemResponse(
                        201L, "애플워치", null, "UP", 150_000L, 180_000L, "SOLD", null
                )),
                List.of(new BuyingItemResponse(
                        91L, "캠핑 텐트", null, "DOWN", 140_000L, 98_000L, "IN_PROGRESS"
                ))
        ));

        mockMvc.perform(get(ENDPOINT).session(authenticatedSession(MEMBER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.profile.nickname").value("급처하는근성"))
                .andExpect(jsonPath("$.data.profile.joinedAt").value(1_752_886_800_000L))
                .andExpect(jsonPath("$.data.profile.sellCount").value(17L))
                .andExpect(jsonPath("$.data.deposit.balance").value(60_000L))
                .andExpect(jsonPath("$.data.deposit.inUse").value(16_000L))
                .andExpect(jsonPath("$.data.activeTrades[0].role").value("BUYER"))
                .andExpect(jsonPath("$.data.activeTrades[0].status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.activeTrades[0].dueAt").doesNotExist())
                .andExpect(jsonPath("$.data.sellingItems[0].auctionType").value("UP"))
                .andExpect(jsonPath("$.data.sellingItems[0].status").value("SOLD"))
                .andExpect(jsonPath("$.data.buyingItems[0].status").value("IN_PROGRESS"));

        verify(myPageService).getMyPage(MEMBER_ID);
    }

    @Test
    void 로그인_세션이_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_401_2"));

        verifyNoInteractions(myPageService);
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
}
