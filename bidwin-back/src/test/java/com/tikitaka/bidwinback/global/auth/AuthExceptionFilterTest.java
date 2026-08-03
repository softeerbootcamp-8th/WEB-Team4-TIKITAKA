package com.tikitaka.bidwinback.global.auth;

import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import tools.jackson.databind.ObjectMapper;

class AuthExceptionFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthExceptionFilter filter = new AuthExceptionFilter(objectMapper);

    @Test
    void 인증_예외가_없으면_응답을_건드리지_않는다() throws ServletException, IOException {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void 인증_예외를_오류_코드의_상태로_변환한다() throws ServletException, IOException {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                throwing(ErrorCode.UNAUTHENTICATED)
        );

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 인증에_실패하면_인증_오류_정보를_반환한다() throws ServletException, IOException {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                throwing(ErrorCode.UNAUTHENTICATED)
        );

        // then
        var body = objectMapper.readTree(response.getContentAsByteArray());
        String error = "%s|%s|%s".formatted(
                body.path("success").asBoolean(),
                body.at("/error/code").asString(),
                body.at("/error/message").asString()
        );
        assertThat(error).isEqualTo(
                "false|MEMBER_401_2|로그인 세션이 없거나 만료되었습니다."
        );
    }

    @Test
    void 인증_실패_응답은_JSON_형식이다() throws ServletException, IOException {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                throwing(ErrorCode.UNAUTHENTICATED)
        );

        // then
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void 인증_불가는_401과_구분된_상태로_응답한다() throws ServletException, IOException {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                throwing(ErrorCode.AUTHENTICATION_UNAVAILABLE)
        );

        // then
        var body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(body.at("/error/code").asString()).isEqualTo("MEMBER_503_1");
    }

    @Test
    void 인증_예외가_아니면_그대로_전파한다() {
        // given
        FilterChain chain = (request, response) -> {
            throw new IllegalStateException("unexpected");
        };

        // when & then
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> filter.doFilter(
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        chain
                ));
    }

    @Test
    void 이미_전송된_응답은_덮어쓰지_않는다() throws ServletException, IOException {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (ignoredRequest, committedResponse) -> {
            committedResponse.getWriter().write("already-sent");
            committedResponse.flushBuffer();
            throw new AuthException(ErrorCode.UNAUTHENTICATED);
        };

        // when
        filter.doFilter(new MockHttpServletRequest(), response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).isEqualTo("already-sent");
    }

    private FilterChain throwing(ErrorCode errorCode) {
        return (request, response) -> {
            throw new AuthException(errorCode);
        };
    }
}
