package com.tikitaka.bidwinback.global.auth;

import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.config.FilterConfig;
import com.tikitaka.bidwinback.global.config.WebMvcConfig;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.application.MemberService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionLoginTest.TestController.class)
@Import({
        SessionLoginTest.TestController.class,
        FilterConfig.class,
        WebMvcConfig.class
})
class SessionLoginTest {

    @RestController
    static class TestController {

        @GetMapping("/test/wiring/me")
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
    private MemberService memberService;

    @Test
    void 로그인_세션이_있으면_현재_회원으로_요청을_처리한다() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthConstant.SESSION_KEY, new AuthMember(7L));
        when(memberService.isActive(7L)).thenReturn(true);

        // when
        var result = mockMvc.perform(get("/test/wiring/me").session(session));

        // then
        result.andExpect(content().string("7"));
    }

    @Test
    void 로그인_세션이_없으면_보호된_요청을_거부한다() throws Exception {
        // given
        var request = get("/test/wiring/me");

        // when
        var result = mockMvc.perform(request);

        // then
        result.andExpect(status().isUnauthorized());
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
}
