package com.tikitaka.bidwinback.member.presentation;

import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthExceptionFilter;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.LoginMemberArgumentResolver;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import com.tikitaka.bidwinback.member.application.MemberProfileImageService;
import com.tikitaka.bidwinback.member.presentation.dto.response.ProfileImageUpdateResponse;
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
import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MyPageProfileImageControllerTest {

    @Mock
    private MemberProfileImageService profileImageService;
    @Mock
    private SessionAuthService sessionAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MyPageProfileImageController(profileImageService)
                )
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
    void 업로드된_이미지로_프로필을_변경한다() throws Exception {
        String objectKey = "profile-images/1/new.jpg";
        when(profileImageService.change(1L, objectKey)).thenReturn(
                new ProfileImageUpdateResponse("https://cdn.example.com/new.jpg")
        );

        mockMvc.perform(patch("/api/v1/mypage/profile-image")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"objectKey":"profile-images/1/new.jpg"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl")
                        .value("https://cdn.example.com/new.jpg"));
    }

    @Test
    void 프로필_이미지를_기본값으로_복원한다() throws Exception {
        when(profileImageService.reset(1L)).thenReturn(
                new ProfileImageUpdateResponse("https://cdn.example.com/default.png")
        );

        mockMvc.perform(delete("/api/v1/mypage/profile-image")
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl")
                        .value("https://cdn.example.com/default.png"));
    }

    @Test
    void 로그인_세션이_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(patch("/api/v1/mypage/profile-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"objectKey":"profile-images/1/new.jpg"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("MEMBER_401_2"));

        verifyNoInteractions(profileImageService);
    }

    private MockHttpSession authenticatedSession() {
        AuthMember authMember = new AuthMember(
                1L,
                0L,
                Instant.parse("2026-08-06T10:00:00Z")
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthConstant.SESSION_KEY, authMember);
        when(sessionAuthService.isAuthenticatable(1L, 0L)).thenReturn(true);
        return session;
    }
}
