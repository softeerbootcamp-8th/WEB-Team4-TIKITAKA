package com.tikitaka.bidwinback.global.auth;

import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class SessionAuthenticationFilter extends OncePerRequestFilter {

    // 유휴 만료만으로는 계속 사용되는 탈취 세션을 종료할 수 없어 로그인 시각부터 수명을 제한한다.
    private static final Duration ABSOLUTE_SESSION_LIFETIME = Duration.ofHours(24);
    private static final List<PathPattern> PUBLIC_POST_PATHS = List.of(
            PathPatternParser.defaultInstance.parse("/api/v1/auth/signups"),
            PathPatternParser.defaultInstance.parse("/api/v1/auth/signups/*/verify"),
            PathPatternParser.defaultInstance.parse("/api/v1/auth/signups/email/send"),
            PathPatternParser.defaultInstance.parse("/api/v1/auth/signups/email/confirm"),
            PathPatternParser.defaultInstance.parse("/api/v1/auth/login"),
            PathPatternParser.defaultInstance.parse("/api/v1/auth/password-resets"),
            PathPatternParser.defaultInstance.parse("/api/v1/auth/password-resets/confirm")
    );
    private static final List<PathPattern> PUBLIC_GET_PATHS = List.of(
            PathPatternParser.defaultInstance.parse("/swagger-ui.html"),
            PathPatternParser.defaultInstance.parse("/swagger-ui/**"),
            PathPatternParser.defaultInstance.parse("/v3/api-docs"),
            PathPatternParser.defaultInstance.parse("/v3/api-docs/**")
    );

    private final SessionAuthService sessionAuthService;
    private final Clock clock;

    public SessionAuthenticationFilter(
            SessionAuthService sessionAuthService,
            Clock clock
    ) {
        this.sessionAuthService = sessionAuthService;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 공개 경로는 인증 회원이 필요하지 않으므로 세션 검증 질의도 하지 않는다.
        if (!requiresAuthentication(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        request.setAttribute(AuthConstant.REQUEST_ATTRIBUTE_KEY, resolveAuthMember(request));
        filterChain.doFilter(request, response);
    }

    private boolean requiresAuthentication(HttpServletRequest request) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return false;
        }

        PathContainer requestPath =
                ServletRequestPathUtils.parse(request).pathWithinApplication();
        boolean isPublicPostPath = HttpMethod.POST.matches(request.getMethod())
                && PUBLIC_POST_PATHS.stream().anyMatch(pattern -> pattern.matches(requestPath));
        boolean isPublicGetPath = HttpMethod.GET.matches(request.getMethod())
                && PUBLIC_GET_PATHS.stream().anyMatch(pattern -> pattern.matches(requestPath));
        return !isPublicPostPath && !isPublicGetPath;
    }

    /**
     * 응답 변환은 AuthExceptionFilter가 담당하므로 인증 실패는 예외로만 알린다.
     */
    private AuthMember resolveAuthMember(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AuthException(ErrorCode.UNAUTHENTICATED);
        }

        try {
            Object attribute = session.getAttribute(AuthConstant.SESSION_KEY);
            if (!(attribute instanceof AuthMember authMember)) {
                // 인증 스냅샷이 아닌 값이 담긴 세션은 신뢰할 수 없으므로 함께 폐기한다.
                session.invalidate();
                throw new AuthException(ErrorCode.UNAUTHENTICATED);
            }

            // DB의 현재 ACTIVE 상태와 인증 버전이 모두 일치할 때만 세션을 신뢰한다.
            if (isWithinAbsoluteLifetime(authMember) && isAuthenticatable(authMember)) {
                return authMember;
            }

            session.invalidate();
        } catch (IllegalStateException ignored) {
            // 로그아웃과 동시에 처리 중인 요청은 인증되지 않은 요청
        }

        throw new AuthException(ErrorCode.UNAUTHENTICATED);
    }

    private boolean isAuthenticatable(AuthMember authMember) {
        try {
            return sessionAuthService.isAuthenticatable(
                    authMember.memberId(),
                    authMember.authVersion()
            );
        } catch (DataAccessException exception) {
            // 검증에 실패한 것이 아니라 검증이 불가능한 상태이므로 401로 오인하게 하지 않는다.
            throw new AuthException(ErrorCode.AUTHENTICATION_UNAVAILABLE);
        }
    }

    private boolean isWithinAbsoluteLifetime(AuthMember authMember) {
        Instant loggedInAt = authMember.loggedInAt();
        return loggedInAt != null
                && loggedInAt.isAfter(clock.instant().minus(ABSOLUTE_SESSION_LIFETIME));
    }
}
