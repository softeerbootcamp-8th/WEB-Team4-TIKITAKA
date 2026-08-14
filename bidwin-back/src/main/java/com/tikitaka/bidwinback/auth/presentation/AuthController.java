package com.tikitaka.bidwinback.auth.presentation;

import com.tikitaka.bidwinback.auth.application.AuthService;
import com.tikitaka.bidwinback.auth.presentation.dto.response.AvailabilityResponse;
import com.tikitaka.bidwinback.auth.presentation.dto.request.EmailAvailabilityRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.EmailVerificationRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.EmailVerificationSendRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.LoginRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.NicknameAvailabilityRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.PasswordChangeRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.PasswordResetRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.SignUpRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.response.SignUpResponse;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.session.SessionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionRepository<?> sessionRepository;

    @PostMapping("/signups/email/verify")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> verifyEmail(
            @Valid @RequestBody EmailAvailabilityRequest request
    ) {
        AvailabilityResponse response = authService.checkEmailAvailability(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/signups/nickname/verify")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> verifyNickname(
            @Valid @RequestBody NicknameAvailabilityRequest request
    ) {
        AvailabilityResponse response = authService.checkNicknameAvailability(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/signups")
    public ResponseEntity<ApiResponse<SignUpResponse>> signup(
            @Valid @RequestBody SignUpRequest request
    ) {
        SignUpResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/signups/email/send")
    public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(
            @Valid @RequestBody EmailVerificationSendRequest request
    ) {
        authService.sendVerificationEmail(request);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/signups/email/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmEmail(
            @Valid @RequestBody EmailVerificationRequest request
    ) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthMember authMember = authService.login(request);

        try {
            establishSession(servletRequest, authMember);
        } catch (SerializationException exception) {
            // 로그인은 공개 경로라 SessionAuthenticationFilter를 거치지 않는다. 클라이언트가
            // 들고 있던 기존 쿠키가 가리키는 세션이 손상되어 있으면 getSession()이 역직렬화
            // 단계에서 바로 이 예외를 던진다. 손상된 세션을 지우지 않으면 재로그인해도 같은
            // 손상된 데이터를 계속 읽어 TTL이 끝날 때까지 로그인이 안 되므로, 지우고 재시도한다.
            discardCorruptedSession(servletRequest);
            try {
                establishSession(servletRequest, authMember);
            } catch (DataAccessException | SerializationException retryException) {
                throw new AuthException(ErrorCode.AUTHENTICATION_UNAVAILABLE);
            }
        } catch (DataAccessException exception) {
            // 자격 검증은 끝났지만 세션을 저장할 수 없으므로 로그인 성공으로 응답하면 안 된다.
            throw new AuthException(ErrorCode.AUTHENTICATION_UNAVAILABLE);
        }

        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    private void establishSession(HttpServletRequest servletRequest, AuthMember authMember) {
        HttpSession session = servletRequest.getSession();
        servletRequest.changeSessionId();
        session.setAttribute(AuthConstant.SESSION_KEY, authMember);
    }

    private void discardCorruptedSession(HttpServletRequest servletRequest) {
        String requestedSessionId = servletRequest.getRequestedSessionId();
        if (requestedSessionId != null) {
            sessionRepository.deleteById(requestedSessionId);
        }
    }

    @GetMapping("/session")
    public ResponseEntity<ApiResponse<Void>> session() {
        // 이 경로는 인증 필터를 통과해야 하므로 200 자체가 유효한 로그인 상태를 뜻한다.
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/password-resets")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        authService.requestPasswordReset(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.successWithoutData());
    }

    @PostMapping("/password-resets/confirm")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // 이미 로그아웃된 세션!
            } catch (DataAccessException exception) {
                // 서버 측 세션이 실제로 삭제됐는지 확인할 수 없다. 성공으로 응답하면
                // 탈취됐거나 다른 곳에 저장된 같은 쿠키가 재시도 없이 TTL/절대 만료
                // 전까지 계속 유효한 자격으로 남으므로, 성공으로 숨기지 않고 알린다.
                throw new AuthException(ErrorCode.AUTHENTICATION_UNAVAILABLE);
            }
        }

        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

}
