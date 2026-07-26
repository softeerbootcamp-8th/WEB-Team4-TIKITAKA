package com.tikitaka.bidwinback.auth.presentation;

import com.tikitaka.bidwinback.auth.application.AuthService;
import com.tikitaka.bidwinback.dto.AvailabilityResponse;
import com.tikitaka.bidwinback.dto.EmailAvailabilityRequest;
import com.tikitaka.bidwinback.dto.NicknameAvailabilityRequest;
import com.tikitaka.bidwinback.dto.SignUpRequest;
import com.tikitaka.bidwinback.dto.SignUpResponse;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
