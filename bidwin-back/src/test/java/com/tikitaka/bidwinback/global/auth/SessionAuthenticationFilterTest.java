package com.tikitaka.bidwinback.global.auth;

import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
    // 필터가 상수로 고정한 절대 만료와 동일한 값이어야 만료 경계를 검증할 수 있다.
    private static final Duration ABSOLUTE_LIFETIME = Duration.ofHours(24);

    private final SessionAuthService sessionAuthService = mock(SessionAuthService.class);
    private final SessionAuthenticationFilter filter =
            new SessionAuthenticationFilter(
                    sessionAuthService,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void 로그인_세션이_있으면_요청에서_인증_회원을_사용할_수_있다()
            throws ServletException, IOException {
        // given
        AuthMember authMember = AuthMemberFixture.of(1L, NOW);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(AuthConstant.SESSION_KEY, authMember);
        when(sessionAuthService.isAuthenticatable(1L, 0L)).thenReturn(true);

        // when
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        // then
        assertThat(request.getAttribute(AuthConstant.REQUEST_ATTRIBUTE_KEY)).isEqualTo(authMember);
    }

    @Test
    void 로그인_세션이_있으면_보호_경로_요청을_통과시킨다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.getSession().setAttribute(AuthConstant.SESSION_KEY, AuthMemberFixture.of(1L, NOW));
        when(sessionAuthService.isAuthenticatable(1L, 0L)).thenReturn(true);
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void 활성_상태가_아닌_회원의_세션은_무효화한다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(AuthConstant.SESSION_KEY, AuthMemberFixture.of(1L, NOW));
        when(sessionAuthService.isAuthenticatable(1L, 0L)).thenReturn(false);

        // when
        assertUnauthenticated(request);

        // then
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 활성_상태가_아닌_회원은_보호_경로에_접근할_수_없다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.getSession().setAttribute(AuthConstant.SESSION_KEY, AuthMemberFixture.of(1L, NOW));
        when(sessionAuthService.isAuthenticatable(1L, 0L)).thenReturn(false);

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 세션과_현재_회원의_인증_버전이_다르면_세션을_무효화한다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(
                AuthConstant.SESSION_KEY,
                AuthMemberFixture.of(1L, 1L, NOW)
        );
        when(sessionAuthService.isAuthenticatable(1L, 1L)).thenReturn(false);

        // when
        assertUnauthenticated(request);

        // then
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 세션과_현재_회원의_인증_버전이_다르면_보호_경로에_접근할_수_없다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.getSession().setAttribute(
                AuthConstant.SESSION_KEY,
                AuthMemberFixture.of(1L, 1L, NOW)
        );
        when(sessionAuthService.isAuthenticatable(1L, 1L)).thenReturn(false);

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 세션_검증_질의가_실패하면_인증_불가로_구분해_알린다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.getSession().setAttribute(AuthConstant.SESSION_KEY, AuthMemberFixture.of(1L, NOW));
        when(sessionAuthService.isAuthenticatable(1L, 0L))
                .thenThrow(new QueryTimeoutException("db down"));

        // when & then
        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> filter.doFilter(
                        request,
                        new MockHttpServletResponse(),
                        new MockFilterChain()
                ))
                .extracting(AuthException::getErrorCode)
                .isEqualTo(ErrorCode.AUTHENTICATION_UNAVAILABLE);
    }

    @Test
    void 세션_검증_질의가_실패하면_세션을_무효화하지_않는다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(AuthConstant.SESSION_KEY, AuthMemberFixture.of(1L, NOW));
        when(sessionAuthService.isAuthenticatable(1L, 0L))
                .thenThrow(new QueryTimeoutException("db down"));

        // when
        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> filter.doFilter(
                        request,
                        new MockHttpServletResponse(),
                        new MockFilterChain()
                ));

        // then
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void 로그인_후_24시간에_도달하면_보호_경로에_접근할_수_없다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.getSession().setAttribute(
                AuthConstant.SESSION_KEY,
                AuthMemberFixture.of(1L, NOW.minus(ABSOLUTE_LIFETIME))
        );

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 절대_만료된_세션은_회원_상태를_조회하지_않는다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.getSession().setAttribute(
                AuthConstant.SESSION_KEY,
                AuthMemberFixture.of(1L, NOW.minus(ABSOLUTE_LIFETIME))
        );

        // when
        assertUnauthenticated(request);

        // then
        verifyNoInteractions(sessionAuthService);
    }

    @Test
    void 로그인_후_24시간이_지나기_전에는_보호_경로에_접근할_수_있다()
            throws ServletException, IOException {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.getSession().setAttribute(
                AuthConstant.SESSION_KEY,
                AuthMemberFixture.of(1L, NOW.minus(ABSOLUTE_LIFETIME).plusNanos(1))
        );
        when(sessionAuthService.isAuthenticatable(1L, 0L)).thenReturn(true);
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
    }

    @Test
    void 로그인_시각이_없는_세션으로는_보호_경로에_접근할_수_없다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.getSession().setAttribute(
                AuthConstant.SESSION_KEY,
                AuthMemberFixture.of(1L, null)
        );

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 세션이_없으면_보호_경로에_접근할_수_없다() {
        // given
        MockHttpServletRequest request = protectedRequest();

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 인증에_실패하면_이후_요청_처리를_중단한다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> filter.doFilter(
                        request,
                        new MockHttpServletResponse(),
                        (ignoredRequest, ignoredResponse) -> filterChainInvoked.set(true)
                ));

        // then
        assertThat(filterChainInvoked).isFalse();
    }

    @Test
    void 로그인_정보가_없는_세션으로는_보호_경로에_접근할_수_없다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.getSession();

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 잘못된_로그인_정보가_있는_세션으로는_보호_경로에_접근할_수_없다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.getSession().setAttribute(AuthConstant.SESSION_KEY, "invalid-auth-member");

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 인증_스냅샷이_아닌_값이_담긴_세션은_무효화한다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(AuthConstant.SESSION_KEY, "invalid-auth-member");

        // when
        assertUnauthenticated(request);

        // then
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 인증에_실패해도_새_세션을_만들지_않는다() {
        // given
        MockHttpServletRequest request = protectedRequest();

        // when
        assertUnauthenticated(request);

        // then
        assertThat(request.getSession(false)).isNull();
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
    void 로그아웃은_세션이_없으면_요청할_수_없다() {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.POST.name(), "/api/v1/auth/logout");
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> filter.doFilter(
                        request,
                        new MockHttpServletResponse(),
                        (ignoredRequest, ignoredResponse) -> filterChainInvoked.set(true)
                ));

        // then
        assertThat(filterChainInvoked).isFalse();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void 로그아웃과_동시에_무효화된_세션은_인증되지_않은_것으로_처리한다() {
        // given
        MockHttpServletRequest request = protectedRequest();
        request.setSession(new MockHttpSession() {
            @Override
            public Object getAttribute(String name) {
                throw new IllegalStateException();
            }
        });

        // when & then
        assertUnauthenticated(request);
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
    void 헬스체크는_세션_없이_요청할_수_있다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.GET.name(),
                "/api/v1/health"
        );
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
        verifyNoInteractions(sessionAuthService);
    }

    @Test
    void 경매_상세_실시간_구독은_세션_없이_요청할_수_있다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.GET.name(),
                "/api/v1/auctions/1/events"
        );
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
        verifyNoInteractions(sessionAuthService);
    }

    @Test
    void 경매_목록_실시간_구독은_세션_없이_요청할_수_있다() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.GET.name(),
                "/api/v1/auctions/events"
        );
        AtomicBoolean filterChainInvoked = new AtomicBoolean();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                filterChainInvoked.set(true)
        );

        // then
        assertThat(filterChainInvoked).isTrue();
        verifyNoInteractions(sessionAuthService);
    }

    @Test
    void 실시간_구독을_열어도_같은_경매의_다른_조회는_인증이_필요하다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.GET.name(),
                "/api/v1/auctions/1/bids"
        );

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 실시간_구독_경로는_경매_식별자_한_구간만_허용한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.GET.name(),
                "/api/v1/auctions/1/nested/events"
        );

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 인증번호_확인_경로는_회원_식별자_한_구간만_허용한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest(
                HttpMethod.POST.name(),
                "/api/v1/auth/signups/123/nested/verify"
        );

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 로그인_경로는_POST_요청만_인증_없이_허용한다() {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.GET.name(), "/api/v1/auth/login");

        // when & then
        assertUnauthenticated(request);
    }

    @Test
    void 로그인_경로_뒤에_슬래시가_붙으면_공개_경로로_취급하지_않는다() {
        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.POST.name(), "/api/v1/auth/login/");

        // when & then
        assertUnauthenticated(request);
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

    private MockHttpServletRequest protectedRequest() {
        return new MockHttpServletRequest(HttpMethod.GET.name(), "/api/auctions");
    }

    // 응답 변환은 AuthExceptionFilter의 책임이므로 여기서는 던진 예외만 확인한다.
    private void assertUnauthenticated(MockHttpServletRequest request) {
        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> filter.doFilter(
                        request,
                        new MockHttpServletResponse(),
                        new MockFilterChain()
                ))
                .extracting(AuthException::getErrorCode)
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }
}
