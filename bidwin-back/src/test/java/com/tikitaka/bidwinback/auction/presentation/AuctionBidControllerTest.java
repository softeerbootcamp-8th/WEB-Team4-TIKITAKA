package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.BidResult;
import com.tikitaka.bidwinback.auction.application.BidService;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
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
class AuctionBidControllerTest {

    private static final long MEMBER_ID = 1L;
    private static final long AUCTION_ID = 42L;
    private static final long BID_ID = 7L;
    private static final long PRICE = 232_000L;
    private static final String ENDPOINT =
            "/api/v1/auctions/" + AUCTION_ID + "/bids";
    private static final String VALID_REQUEST = """
            {
              "price": 232000
            }
            """;

    @Mock
    private BidService bidService;

    @Mock
    private SessionAuthService sessionAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuctionBidController(bidService))
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
        when(bidService.place(MEMBER_ID, AUCTION_ID, PRICE)).thenReturn(result);

        // when & then
        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bidId").value(BID_ID))
                .andExpect(jsonPath("$.data.auctionId").value(AUCTION_ID))
                .andExpect(jsonPath("$.data.bidderId").value(MEMBER_ID))
                .andExpect(jsonPath("$.data.price").value(PRICE))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.bidAt").value(bidAt.toString()));

        verify(bidService).place(MEMBER_ID, AUCTION_ID, PRICE);
    }

    @Test
    void 입찰가가_0_이하면_400을_응답한다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "price": 0
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
    void 로그인_세션이_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_401_2"));

        verifyNoInteractions(bidService);
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
