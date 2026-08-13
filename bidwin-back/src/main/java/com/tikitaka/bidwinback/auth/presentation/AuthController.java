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
import com.tikitaka.bidwinback.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/signups/email/bypass")
    public ResponseEntity<ApiResponse<Void>> bypassEmailVerification(
            @Valid @RequestBody EmailVerificationSendRequest request
    ) {
        authService.bypassEmailVerification(request);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthMember authMember = authService.login(request);
        HttpSession session = servletRequest.getSession();
        servletRequest.changeSessionId();
        session.setAttribute(AuthConstant.SESSION_KEY, authMember);

        return ResponseEntity.ok(ApiResponse.successWithoutData());
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
            }
        }

        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

}
