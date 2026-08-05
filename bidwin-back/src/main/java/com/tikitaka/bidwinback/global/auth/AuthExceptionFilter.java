package com.tikitaka.bidwinback.global.auth;

import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.ObjectMapper;

/**
 * 필터 단계에서 발생한 인증 예외를 응답 규약으로 변환한다.
 * DispatcherServlet 앞에서 끝나는 요청은 GlobalExceptionHandler가 처리할 수 없으므로
 * 인증 필터는 예외를 던지는 책임만 지고 응답 변환은 이 필터가 담당한다.
 */
public class AuthExceptionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public AuthExceptionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (AuthException exception) {
            writeErrorResponse(response, exception.getErrorCode());
        }
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {
        // 이미 응답이 나간 뒤라면 상태 코드를 덮어쓸 수 없다.
        if (response.isCommitted()) {
            return;
        }

        HttpStatus status = errorCode.getStatus();
        // CORS처럼 앞선 필터가 추가한 응답 헤더는 유지하고 미완성 본문만 비운다.
        response.resetBuffer();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode));
    }
}
