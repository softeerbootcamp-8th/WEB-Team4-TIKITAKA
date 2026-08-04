package com.tikitaka.bidwinback.upload.presentation;

import com.tikitaka.bidwinback.auth.application.SessionAuthService;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthExceptionFilter;
import com.tikitaka.bidwinback.global.auth.AuthMemberFixture;
import com.tikitaka.bidwinback.global.auth.SessionAuthenticationFilter;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import com.tikitaka.bidwinback.upload.application.AuctionImageDraftService;
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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private static final String DRAFT_ENDPOINT =
            "/api/v1/uploads/auction-images/drafts";
    private static final UUID DRAFT_ID =
            UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09");
    private static final String VALID_REQUEST = """
            {
              "draftId": "44eac1aa-827d-40c3-b3e9-c44abb94ed09",
              "images": [
                {
                  "fileName": "headphone.jpg",
                  "contentType": "image/jpeg",
                  "size": 248392
                },
                {
                  "fileName": "keyboard.png",
                  "contentType": "image/png",
                  "size": 128000
                }
              ]
            }
            """;

    @Mock
    private AuctionImagePresignService presignService;

    @Mock
    private AuctionImageDraftService draftService;

    @Mock
    private SessionAuthService sessionAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuctionImageUploadController(
                        presignService,
                        draftService
                ))
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
    void 로그인한_회원이_작성_페이지에_진입하면_draftId를_발급한다()
            throws Exception {
        when(draftService.issue()).thenReturn(DRAFT_ID);

        mockMvc.perform(post(DRAFT_ENDPOINT)
                        .session(authenticatedSession()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.draftId").value(DRAFT_ID.toString()));

        verify(draftService).issue();
    }

    @Test
    void 로그인한_회원이_요청하면_이미지별_Presigned_URL과_201을_응답한다()
            throws Exception {
        List<AuctionImagePresignRequest> requests = List.of(
                new AuctionImagePresignRequest(
                        "headphone.jpg",
                        "image/jpeg",
                        248_392L
                ),
                new AuctionImagePresignRequest(
                        "keyboard.png",
                        "image/png",
                        128_000L
                )
        );
        List<AuctionImagePresignResponse> responses = List.of(
                new AuctionImagePresignResponse(
                        "https://example.com/presigned-upload-1",
                        "auction-images/image-id-1.jpg",
                        Map.of("Content-Type", List.of("image/jpeg")),
                        Instant.parse("2026-07-28T06:10:00Z")
                ),
                new AuctionImagePresignResponse(
                        "https://example.com/presigned-upload-2",
                        "auction-images/image-id-2.png",
                        Map.of("Content-Type", List.of("image/png")),
                        Instant.parse("2026-07-28T06:10:00Z")
                )
        );
        when(presignService.issue(1L, DRAFT_ID, requests)).thenReturn(responses);

        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].presignedUrl")
                        .value(responses.get(0).presignedUrl()))
                .andExpect(jsonPath("$.data[0].objectKey")
                        .value(responses.get(0).objectKey()))
                .andExpect(jsonPath(
                        "$.data[0].signedHeaders['Content-Type'][0]"
                ).value("image/jpeg"))
                .andExpect(jsonPath("$.data[1].presignedUrl")
                        .value(responses.get(1).presignedUrl()))
                .andExpect(jsonPath("$.data[1].objectKey")
                        .value(responses.get(1).objectKey()))
                .andExpect(jsonPath(
                        "$.data[1].signedHeaders['Content-Type'][0]"
                ).value("image/png"));

        verify(presignService).issue(1L, DRAFT_ID, requests);
    }

    @Test
    void 파일이_10MB를_초과하면_400을_응답한다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "draftId": "44eac1aa-827d-40c3-b3e9-c44abb94ed09",
                                  "images": [
                                    {
                                      "fileName": "headphone.jpg",
                                      "contentType": "image/jpeg",
                                      "size": 10485761
                                    }
                                  ]
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
    void 이미지가_없으면_400을_응답한다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "draftId": "44eac1aa-827d-40c3-b3e9-c44abb94ed09",
                                  "images": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("COMMON_400_1"))
                .andExpect(jsonPath("$.error.message")
                        .value("이미지는 한 장 이상이어야 합니다."));

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
        session.setAttribute(AuthConstant.SESSION_KEY, AuthMemberFixture.of(1L));
        when(sessionAuthService.isAuthenticatable(1L, 0L)).thenReturn(true);
        return session;
    }
}
