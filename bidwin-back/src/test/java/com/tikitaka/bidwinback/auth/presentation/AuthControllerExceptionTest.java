package com.tikitaka.bidwinback.auth.presentation;

import com.tikitaka.bidwinback.auth.application.AuthService;
import com.tikitaka.bidwinback.dto.LoginRequest;
import com.tikitaka.bidwinback.dto.SignUpRequest;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerExceptionTest {

    private static final String VALID_LOGIN_REQUEST = """
            {
              "email": "member@example.com",
              "password": "password!"
            }
            """;

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

    @Test
    void 로그인에_성공하면_세션과_200을_응답한다() throws Exception {
        // given
        AuthMember authMember = new AuthMember(1L);
        MockHttpSession session = new MockHttpSession();
        when(authService.login(any(LoginRequest.class))).thenReturn(authMember);

        // when
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_LOGIN_REQUEST));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(request().sessionAttribute(
                        AuthConstant.SESSION_KEY,
                        authMember
                ));
    }

    @Test
    void 로그인_정보가_일치하지_않으면_세션_없이_401을_응답한다() throws Exception {
        // given
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AuthException(ErrorCode.INVALID_CREDENTIALS));

        // when
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_LOGIN_REQUEST));

        // then
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MEMBER_401_1"));
        assertNull(result.andReturn().getRequest().getSession(false));
    }

    @Test
    void 로그인_입력값이_올바르지_않으면_인증하지_않고_400을_응답한다() throws Exception {
        // given
        String invalidRequest = """
                {
                  "email": "invalid-email",
                  "password": ""
                }
                """;

        // when
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"));
        verifyNoInteractions(authService);
    }
}
