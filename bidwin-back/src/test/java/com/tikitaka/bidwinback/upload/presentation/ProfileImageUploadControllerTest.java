package com.tikitaka.bidwinback.upload.presentation;

import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthExceptionFilter;
import com.tikitaka.bidwinback.global.auth.AuthMemberFixture;
import com.tikitaka.bidwinback.global.auth.LoginMemberArgumentResolver;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import com.tikitaka.bidwinback.upload.application.ProfileImagePresignService;
import com.tikitaka.bidwinback.upload.presentation.dto.ProfileImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.ProfileImagePresignResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileImageUploadControllerTest {

    @Mock
    private ProfileImagePresignService presignService;
    @Mock
    private SessionAuthService sessionAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ProfileImageUploadController(presignService)
                )
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(
                        new AuthExceptionFilter(new ObjectMapper()),
                        new SessionAuthenticationFilter(
                                sessionAuthService,
                                Clock.systemUTC()
                        )
                )
                .build();
    }

    @Test
    void 프로필_이미지_Presigned_URL과_201을_응답한다() throws Exception {
        ProfileImagePresignRequest request = new ProfileImagePresignRequest(
                "profile.jpg",
                "image/jpeg",
                123_456L
        );
        ProfileImagePresignResponse response = new ProfileImagePresignResponse(
                "https://example.com/upload",
                "profile-images/1/image.jpg",
                Map.of("Content-Type", List.of("image/jpeg")),
                Instant.parse("2026-08-06T12:05:00Z")
        );
        when(presignService.issue(1L, request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/uploads/profile-images/presign")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName":"profile.jpg",
                                  "contentType":"image/jpeg",
                                  "size":123456
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.presignedUrl")
                        .value("https://example.com/upload"))
                .andExpect(jsonPath("$.data.objectKey")
                        .value("profile-images/1/image.jpg"));
    }

    @Test
    void 파일이_5MB를_초과하면_400을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/profile-images/presign")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName":"profile.jpg",
                                  "contentType":"image/jpeg",
                                  "size":5242881
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400_1"));

        verifyNoInteractions(presignService);
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthConstant.SESSION_KEY, AuthMemberFixture.of(1L));
        when(sessionAuthService.isAuthenticatable(1L, 0L)).thenReturn(true);
        return session;
    }
}
