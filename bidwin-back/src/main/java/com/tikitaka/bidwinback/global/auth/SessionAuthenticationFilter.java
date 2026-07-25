package com.tikitaka.bidwinback.global.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class SessionAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        resolveAuthMember(request)
                .ifPresent(authMember ->
                        request.setAttribute(AuthConstant.REQUEST_ATTRIBUTE_KEY, authMember)
                );

        filterChain.doFilter(request, response);
    }

    private Optional<AuthMember> resolveAuthMember(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {  
            return Optional.empty();
        }

        Object attribute = session.getAttribute(AuthConstant.SESSION_KEY);
        if (attribute instanceof AuthMember authMember) {
            return Optional.of(authMember);
        }

        return Optional.empty();
    }
}
