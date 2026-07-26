package com.tikitaka.bidwinback.auth.presentation;

import com.tikitaka.bidwinback.auth.application.AuthService;
import com.tikitaka.bidwinback.dto.LoginRequest;
import com.tikitaka.bidwinback.dto.AvailabilityResponse;
import com.tikitaka.bidwinback.dto.EmailAvailabilityRequest;
import com.tikitaka.bidwinback.dto.NicknameAvailabilityRequest;
import com.tikitaka.bidwinback.dto.SignUpRequest;
import com.tikitaka.bidwinback.dto.SignUpResponse;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void 이메일이_사용_가능하면_확인_결과와_200을_응답한다() {
        AuthService authService = mock(AuthService.class);
        EmailAvailabilityRequest request =
                new EmailAvailabilityRequest("member@example.com");
        AvailabilityResponse expected = new AvailabilityResponse(true);
        when(authService.checkEmailAvailability(request)).thenReturn(expected);

        ResponseEntity<ApiResponse<AvailabilityResponse>> result =
                new AuthController(authService).verifyEmail(request);

        assertAll(
                () -> assertEquals(HttpStatus.OK, result.getStatusCode()),
                () -> assertSame(expected, result.getBody().data())
        );
    }

    @Test
    void 닉네임이_사용_가능하면_확인_결과와_200을_응답한다() {
        AuthService authService = mock(AuthService.class);
        NicknameAvailabilityRequest request = new NicknameAvailabilityRequest("티키타카");
        AvailabilityResponse expected = new AvailabilityResponse(true);
        when(authService.checkNicknameAvailability(request)).thenReturn(expected);

        ResponseEntity<ApiResponse<AvailabilityResponse>> result =
                new AuthController(authService).verifyNickname(request);

        assertAll(
                () -> assertEquals(HttpStatus.OK, result.getStatusCode()),
                () -> assertSame(expected, result.getBody().data())
        );
    }

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

    @Test
    void 로그인하면_세션_ID를_교체하고_인증_회원을_저장한다() {
        // given
        AuthService authService = mock(AuthService.class);
        LoginRequest request = new LoginRequest("member@example.com", "password!");
        AuthMember authMember = new AuthMember(1L);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        String previousSessionId = servletRequest.getSession().getId();
        when(authService.login(request)).thenReturn(authMember);

        // when
        ResponseEntity<ApiResponse<Void>> result =
                new AuthController(authService).login(request, servletRequest);

        // then
        assertAll(
                () -> assertEquals(HttpStatus.OK, result.getStatusCode()),
                () -> assertNotEquals(
                        previousSessionId,
                        servletRequest.getSession().getId()
                ),
                () -> assertSame(
                        authMember,
                        servletRequest.getSession()
                                .getAttribute(AuthConstant.SESSION_KEY)
                )
        );
    }
}
