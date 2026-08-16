package com.tikitaka.bidwinback.upload.presentation;

import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.config.OpenApiConfig;
import com.tikitaka.bidwinback.upload.application.AuctionImageDraftService;
import com.tikitaka.bidwinback.upload.application.AuctionImagePresignService;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImageDraftResponse;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignBatchRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/uploads/auction-images")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
@Tag(name = "이미지 업로드", description = "S3 직접 업로드용 Presigned URL 발급")
public class AuctionImageUploadController {

    private final AuctionImagePresignService presignService;
    private final AuctionImageDraftService draftService;

    @Operation(summary = "경매 이미지 draft 발급", description = "경매 등록 전 이미지 업로드 묶음을 식별할 임시 draft ID를 발급합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "draft 발급 완료")
    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<AuctionImageDraftResponse>> issueDraft() {
        AuctionImageDraftResponse response = new AuctionImageDraftResponse(draftService.issue());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(
            summary = "경매 이미지 업로드 URL 발급",
            description = "최대 10개 이미지의 형식·크기·SHA-256 체크섬을 검증하고 S3 PUT용 Presigned URL을 발급합니다. 반환된 uploadId를 경매 등록 요청에 사용합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Presigned URL 발급 완료")
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<List<AuctionImagePresignResponse>>> presign(
            @Login AuthMember authMember,
            @Valid @RequestBody AuctionImagePresignBatchRequest request
    ) {
        List<AuctionImagePresignResponse> response = presignService.issue(
                authMember.memberId(),
                request.draftId(),
                request.images()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
