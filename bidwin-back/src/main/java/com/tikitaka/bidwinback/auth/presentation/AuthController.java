package com.tikitaka.bidwinback.auth.presentation;

import com.tikitaka.bidwinback.auth.application.AuthService;
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

    @PostMapping("/signups")
    public ResponseEntity<ApiResponse<SignUpResponse>> signup(
            @Valid @RequestBody SignUpRequest request
    ) {
        SignUpResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
