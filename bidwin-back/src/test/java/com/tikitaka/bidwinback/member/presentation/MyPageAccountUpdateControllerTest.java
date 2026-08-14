package com.tikitaka.bidwinback.member.presentation;

import com.tikitaka.bidwinback.auth.application.AuthenticatedPasswordChangeService;
import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthExceptionFilter;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.LoginMemberArgumentResolver;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import com.tikitaka.bidwinback.member.application.MemberService;
import com.tikitaka.bidwinback.member.presentation.dto.request.PasswordUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MyPageAccountUpdateControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-06T10:00:00Z");

    @Mock
    private MemberService memberService;

    @Mock
    private AuthenticatedPasswordChangeService passwordChangeService;

    @Mock
    private SessionAuthService sessionAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MyPageAccountUpdateController(
                                memberService,
                                passwordChangeService
                        )
                )
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(
                        new AuthExceptionFilter(new ObjectMapper()),
                        new SessionAuthenticationFilter(
                                sessionAuthService,
                                Clock.fixed(NOW, ZoneOffset.UTC)
                        )
                )
                .build();
    }

    @Test
    void 닉네임을_변경하면_변경된_값과_200을_응답한다() throws Exception {
        AuthMember authMember = authMember();
        when(memberService.changeNickname(1L, "새닉네임")).thenReturn("새닉네임");

        mockMvc.perform(patch("/api/v1/mypage/nickname")
                        .session(authenticatedSession(authMember))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새닉네임"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
    }

    @Test
    void 닉네임에_허용되지_않은_문자가_있으면_400을_응답한다() throws Exception {
        mockMvc.perform(patch("/api/v1/mypage/nickname")
                        .session(authenticatedSession(authMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"닉네임🙂"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"));

        verifyNoInteractions(memberService);
    }

    @Test
    void 비밀번호를_변경하면_세션_ID와_인증_버전을_갱신한다() throws Exception {
        AuthMember currentAuth = authMember();
        AuthMember refreshedAuth = new AuthMember(
                1L,
                3L,
                currentAuth.loggedInAt()
        );
        MockHttpSession session = authenticatedSession(currentAuth);
        String previousSessionId = session.getId();
        when(passwordChangeService.change(
                currentAuth,
                "current-password!",
                "new-password!",
                "new-password!"
        )).thenReturn(refreshedAuth);

        mockMvc.perform(patch("/api/v1/mypage/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"current-password!",
                                  "newPassword":"new-password!",
                                  "newPasswordConfirm":"new-password!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(session.getId()).isNotEqualTo(previousSessionId);
        assertThat(session.getAttribute(AuthConstant.SESSION_KEY))
                .isSameAs(refreshedAuth);
    }

    @Test
    void 비밀번호_변경_후_세션_갱신에_실패하면_재로그인을_안내한다() {
        // given
        AuthMember currentAuth = authMember();
        AuthMember refreshedAuth = new AuthMember(1L, 3L, currentAuth.loggedInAt());
        when(passwordChangeService.change(
                currentAuth,
                "current-password!",
                "new-password!",
                "new-password!"
        )).thenReturn(refreshedAuth);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        doThrow(new DataAccessResourceFailureException("Redis 장애"))
                .when(session).setAttribute(AuthConstant.SESSION_KEY, refreshedAuth);
        PasswordUpdateRequest request = new PasswordUpdateRequest(
                "current-password!", "new-password!", "new-password!"
        );

        // when & then
        AuthException exception = assertThrows(
                AuthException.class,
                () -> new MyPageAccountUpdateController(memberService, passwordChangeService)
                        .changePassword(currentAuth, request, servletRequest)
        );
        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void 로그인_세션이_없으면_변경_API는_401을_응답한다() throws Exception {
        mockMvc.perform(patch("/api/v1/mypage/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새닉네임"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("MEMBER_401_2"));

        verifyNoInteractions(memberService);
    }

    private AuthMember authMember() {
        return new AuthMember(
                1L,
                2L,
                NOW
        );
    }

    private MockHttpSession authenticatedSession(AuthMember authMember) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthConstant.SESSION_KEY, authMember);
        when(sessionAuthService.isAuthenticatable(
                authMember.memberId(),
                authMember.authVersion()
        )).thenReturn(true);
        return session;
    }
}
