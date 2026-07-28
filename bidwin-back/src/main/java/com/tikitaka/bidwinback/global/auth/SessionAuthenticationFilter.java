package com.tikitaka.bidwinback.global.auth;

import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import tools.jackson.databind.ObjectMapper;

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

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SessionAuthenticationFilter(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    SessionAuthenticationFilter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<AuthMember> authMember = resolveAuthMember(request);
        authMember.ifPresent(member ->
                request.setAttribute(AuthConstant.REQUEST_ATTRIBUTE_KEY, member)
        );

        if (authMember.isEmpty() && requiresAuthentication(request)) {
            writeUnauthorizedResponse(response);
            return;
        }

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

    private Optional<AuthMember> resolveAuthMember(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }

        try {
            Object attribute = session.getAttribute(AuthConstant.SESSION_KEY);
            if (attribute instanceof AuthMember authMember && isWithinAbsoluteLifetime(authMember)) {
                return Optional.of(authMember);
            }
        } catch (IllegalStateException ignored) {
            // 로그아웃과 동시에 처리 중인 요청은 인증되지 않은 요청
        }

        return Optional.empty();
    }

    private boolean isWithinAbsoluteLifetime(AuthMember authMember) {
        Instant loggedInAt = authMember.loggedInAt();
        return loggedInAt != null
                && loggedInAt.isAfter(clock.instant().minus(ABSOLUTE_SESSION_LIFETIME));
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode));
    }
}
