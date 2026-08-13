package com.tikitaka.bidwinback.member.presentation;

import com.tikitaka.bidwinback.auth.application.AuthenticatedPasswordChangeService;
import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthExceptionFilter;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.LoginMemberArgumentResolver;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import com.tikitaka.bidwinback.member.application.MemberService;
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
import java.time.ZoneOffset;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                eq(currentAuth),
                eq("current-password!"),
                eq("new-password!"),
                eq("new-password!"),
                any()
        )).thenAnswer(invocation -> {
            Consumer<AuthMember> onPasswordChanged = invocation.getArgument(4);
            onPasswordChanged.accept(refreshedAuth);
            return refreshedAuth;
        });

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
