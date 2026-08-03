package com.tikitaka.bidwinback.global.auth;

import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.config.ClockConfig;
import com.tikitaka.bidwinback.global.config.FilterConfig;
import com.tikitaka.bidwinback.global.config.WebMvcConfig;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;

@WebMvcTest(SessionLoginTest.TestController.class)
@Import({
        SessionLoginTest.TestController.class,
        ClockConfig.class,
        FilterConfig.class,
        WebMvcConfig.class
})
class SessionLoginTest {

    @RestController
    static class TestController {

        @GetMapping("/api/v1/auth/session")
        String me(@Login AuthMember authMember) {
            return String.valueOf(authMember.memberId());
        }

        @PostMapping("/api/v1/auth/login")
        String login() {
            return "login";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionAuthService sessionAuthService;

    @Test
    void 로그인_세션이_있으면_현재_회원으로_요청을_처리한다() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthConstant.SESSION_KEY, AuthMemberFixture.of(7L));
        when(sessionAuthService.isAuthenticatable(7L, 0L)).thenReturn(true);

        // when
        var result = mockMvc.perform(get("/api/v1/auth/session").session(session));

        // then
        result.andExpect(content().string("7"));
    }

    @Test
    void 로그인_세션이_없으면_보호된_요청을_거부한다() throws Exception {
        // given
        var request = get("/api/v1/auth/session");

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isUnauthorized());
    }

    @Test
    void 프론트_오리진의_인증_실패_응답에도_CORS_헤더를_포함한다() throws Exception {
        // given
        var request = get("/api/v1/auth/session")
                .header(ORIGIN, "https://bidwin.site");

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "https://bidwin.site"))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void 요청에_로그인_회원이_없으면_인증_예외를_던진다() {
        // given
        var resolver = new LoginMemberArgumentResolver();
        var webRequest = new ServletWebRequest(new MockHttpServletRequest());

        // when & then
        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .extracting(AuthException::getErrorCode)
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void 공개_경로는_로그인_세션_없이_요청할_수_있다() throws Exception {
        // given
        var request = post("/api/v1/auth/login");

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(content().string("login"));
    }

    @Test
    void 프론트_오리진의_로그인_preflight를_허용한다() throws Exception {
        // given
        var request = options("/api/v1/auth/login")
                .header(ORIGIN, "https://bidwin.site")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "POST");

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "https://bidwin.site"))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void 허용하지_않은_오리진의_preflight를_거부한다() throws Exception {
        // given
        var request = options("/api/v1/auth/login")
                .header(ORIGIN, "https://attacker.example")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "POST");

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
