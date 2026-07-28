package com.tikitaka.bidwinback.upload.presentation;

import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import com.tikitaka.bidwinback.upload.application.AuctionImagePresignService;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignResponse;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuctionImageUploadControllerTest {

    private static final String ENDPOINT =
            "/api/v1/uploads/auction-images/presign";
    private static final String VALID_REQUEST = """
            {
              "fileName": "headphone.jpg",
              "contentType": "image/jpeg",
              "size": 248392
            }
            """;

    @Mock
    private AuctionImagePresignService presignService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuctionImageUploadController(presignService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new SessionAuthenticationFilter(new ObjectMapper()))
                .build();
    }

    @Test
    void 로그인한_회원이_요청하면_Presigned_URL과_서명된_헤더와_201을_응답한다()
            throws Exception {
        AuctionImagePresignRequest request = new AuctionImagePresignRequest(
                "headphone.jpg",
                "image/jpeg",
                248_392L
        );
        AuctionImagePresignResponse response = new AuctionImagePresignResponse(
                "https://example.com/presigned-upload",
                "auction-images/image-id.jpg",
                Map.of("Content-Type", List.of("image/jpeg")),
                Instant.parse("2026-07-28T06:10:00Z")
        );
        when(presignService.issue(request)).thenReturn(response);

        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.presignedUrl")
                        .value(response.presignedUrl()))
                .andExpect(jsonPath("$.data.objectKey")
                        .value(response.objectKey()))
                .andExpect(jsonPath(
                        "$.data.signedHeaders['Content-Type'][0]"
                ).value("image/jpeg"))
                .andExpect(jsonPath("$.data.expiresAt")
                        .value("2026-07-28T06:10:00Z"));

        verify(presignService).issue(request);
    }

    @Test
    void 파일이_10MB를_초과하면_400을_응답한다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "headphone.jpg",
                                  "contentType": "image/jpeg",
                                  "size": 10485761
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("COMMON_400_1"))
                .andExpect(jsonPath("$.error.message")
                        .value("파일 크기는 10MB 이하여야 합니다."));

        verifyNoInteractions(presignService);
    }

    @Test
    void 로그인_세션이_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("MEMBER_401_2"));

        verifyNoInteractions(presignService);
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthConstant.SESSION_KEY, new AuthMember(1L));
        return session;
    }
}
