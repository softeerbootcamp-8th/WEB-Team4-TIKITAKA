package com.tikitaka.bidwinback.auth.presentation;

import com.tikitaka.bidwinback.auth.application.AuthService;
import com.tikitaka.bidwinback.dto.SignUpRequest;
import com.tikitaka.bidwinback.dto.SignUpResponse;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void 회원가입_성공_시_생성된_회원과_201을_응답한다() {
        AuthService authService = mock(AuthService.class);
        SignUpRequest request = new SignUpRequest(
                "member@example.com",
                "password!",
                "홍길동",
                "01012345678",
                "티키타카"
        );
        SignUpResponse expected = new SignUpResponse(1L, request.email(), request.nickname());
        when(authService.signup(request)).thenReturn(expected);

        ResponseEntity<ApiResponse<SignUpResponse>> result =
                new AuthController(authService).signup(request);

        assertAll(
                () -> assertEquals(HttpStatus.CREATED, result.getStatusCode()),
                () -> assertSame(expected, result.getBody().data())
        );
    }
}
