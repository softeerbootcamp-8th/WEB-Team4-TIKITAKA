package com.tikitaka.bidwinback.global.auth;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;

class SessionAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionAuthenticationFilter filter =
            new SessionAuthenticationFilter(objectMapper);
    private final SessionAuthenticationFilter fixedTimeFilter =
            new SessionAuthenticationFilter(
                    objectMapper,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void 로그인_세션이_있으면_요청에서_인증_회원을_사용할_수_있다()
            throws ServletException, IOException {
        // given
        AuthMember authMember = new AuthMember(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(AuthConstant.SESSION_KEY, authMember);

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(request.getAttribute(AuthConstant.REQUEST_ATTRIBUTE_KEY)).isEqualTo(authMember);
    }

    @Test
    void 로그인한_회원은_보호_경로에_접근할_수_있다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        request.getSession().setAttribute(AuthConstant.SESSION_KEY, new AuthMember(1L));
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void 로그인_후_24시간에_도달하면_보호_경로에_접근할_수_없다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        request.getSession().setAttribute(
                AuthConstant.SESSION_KEY,
                new AuthMember(1L, NOW.minus(Duration.ofHours(24)))
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        fixedTimeFilter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 로그인_후_24시간이_지나기_전에는_보호_경로에_접근할_수_있다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        request.getSession().setAttribute(
                AuthConstant.SESSION_KEY,
                new AuthMember(
                        1L,
                        NOW.minus(Duration.ofHours(24)).plusNanos(1)
                )
        );
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        fixedTimeFilter.doFilter(
                request,
                new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void 로그인_시각이_없는_세션으로는_보호_경로에_접근할_수_없다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        request.getSession().setAttribute(
                AuthConstant.SESSION_KEY,
                new AuthMember(1L, null)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        fixedTimeFilter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 세션이_없으면_보호_경로에_접근할_수_없다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 인증에_실패하면_이후_요청_처리를_중단한다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isFalse();
    }

    @Test
    void 로그인_정보가_없는_세션으로는_보호_경로에_접근할_수_없다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 잘못된_로그인_정보가_있는_세션으로는_보호_경로에_접근할_수_없다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        request.getSession().setAttribute(AuthConstant.SESSION_KEY, "invalid-auth-member");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 인증에_실패해도_새_세션을_만들지_않는다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void 인증에_실패하면_인증_오류_정보를_반환한다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

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
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void 로그인은_세션_없이_요청할_수_있다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.POST.name(), "/api/v1/auth/login");
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void 로그아웃은_세션이_없으면_요청할_수_없다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.POST.name(), "/api/v1/auth/logout");
        AtomicBoolean filterChainInvoked = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(filterChainInvoked).isFalse();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void 로그아웃과_동시에_무효화된_세션은_인증되지_않은_것으로_처리한다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
        request.setSession(new MockHttpSession() {
            @Override
            public Object getAttribute(String name) {
                throw new IllegalStateException();
            }
        });
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 회원가입은_세션_없이_요청할_수_있다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.POST.name(), "/api/v1/auth/signups");
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void 인증번호_확인은_세션_없이_요청할_수_있다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.POST.name(),
                "/api/v1/auth/signups/123/verify"
        );
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void 비밀번호_재설정은_세션_없이_요청할_수_있다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.POST.name(),
                "/api/v1/auth/password-resets"
        );
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void 비밀번호_재설정_확인은_세션_없이_요청할_수_있다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.POST.name(),
                "/api/v1/auth/password-resets/confirm"
        );
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void Swagger_UI는_세션_없이_요청할_수_있다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.GET.name(),
                "/swagger-ui/index.html"
        );
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void OpenAPI_문서는_세션_없이_요청할_수_있다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.GET.name(),
                "/v3/api-docs"
        );
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void 인증번호_확인_경로는_회원_식별자_한_구간만_허용한다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.POST.name(),
                "/api/v1/auth/signups/123/nested/verify"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 로그인_경로는_POST_요청만_인증_없이_허용한다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 로그인_경로_뒤에_슬래시가_붙으면_공개_경로로_취급하지_않는다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.POST.name(), "/api/v1/auth/login/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 컨텍스트_경로가_있어도_로그인을_인증_없이_요청할_수_있다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.POST.name(), "/bidwin/api/v1/auth/login");
        request.setContextPath("/bidwin");
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void CORS_프리플라이트_요청은_세션_없이_통과한다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/api/auctions");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:5173");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name());
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }
}
