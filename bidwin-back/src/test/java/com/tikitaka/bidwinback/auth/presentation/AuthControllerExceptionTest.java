package com.tikitaka.bidwinback.auth.presentation;

import com.tikitaka.bidwinback.auth.application.AuthService;
import com.tikitaka.bidwinback.dto.EmailAvailabilityRequest;
import com.tikitaka.bidwinback.dto.NicknameAvailabilityRequest;
import com.tikitaka.bidwinback.dto.SignUpRequest;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerExceptionTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 회원가입_이메일이_중복되면_409를_응답한다() throws Exception {
        when(authService.signup(any(SignUpRequest.class)))
                .thenThrow(new MemberException(ErrorCode.DUPLICATE_EMAIL));

        mockMvc.perform(post("/api/v1/auth/signups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "password": "password!",
                                  "name": "홍길동",
                                  "phoneNumber": "01012345678",
                                  "nickname": "티키타카"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_409_1"));
    }

    @Test
    void 회원가입_닉네임이_중복되면_409를_응답한다() throws Exception {
        when(authService.signup(any(SignUpRequest.class)))
                .thenThrow(new MemberException(ErrorCode.DUPLICATE_NICKNAME));

        mockMvc.perform(post("/api/v1/auth/signups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "password": "password!",
                                  "name": "홍길동",
                                  "phoneNumber": "01012345678",
                                  "nickname": "티키타카"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_409_2"));
    }

    @Test
    void 중복된_이메일이면_409를_응답한다() throws Exception {
        when(authService.checkEmailAvailability(any(EmailAvailabilityRequest.class)))
                .thenThrow(new MemberException(ErrorCode.DUPLICATE_EMAIL));

        mockMvc.perform(post("/api/v1/auth/signups/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "member@example.com"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_409_1"));
    }

    @Test
    void 중복된_닉네임이면_409를_응답한다() throws Exception {
        when(authService.checkNicknameAvailability(any(NicknameAvailabilityRequest.class)))
                .thenThrow(new MemberException(ErrorCode.DUPLICATE_NICKNAME));

        mockMvc.perform(post("/api/v1/auth/signups/nickname/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "티키타카"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_409_2"));
    }

    @Test
    void 이메일_형식이_올바르지_않으면_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signups/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"));
    }

    @Test
    void 닉네임_형식이_올바르지_않으면_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signups/nickname/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "한"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"));
    }

    @Test
    void 요청_본문이_비어있으면_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signups")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"));
    }

    @Test
    void JSON_형식이_올바르지_않으면_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"));
    }

    @Test
    void 요청값_검증에_실패하면_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "",
                                  "password": "",
                                  "name": "",
                                  "phoneNumber": "",
                                  "nickname": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"));
    }

    @Test
    void 내부_처리에_실패하면_상세_정보_없이_500을_응답한다() throws Exception {
        when(authService.signup(any(SignUpRequest.class)))
                .thenThrow(new IllegalStateException("sensitive internal detail"));

        mockMvc.perform(post("/api/v1/auth/signups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "password": "password!",
                                  "name": "홍길동",
                                  "phoneNumber": "01012345678",
                                  "nickname": "티키타카"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_500_1"))
                .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."));
    }
}
