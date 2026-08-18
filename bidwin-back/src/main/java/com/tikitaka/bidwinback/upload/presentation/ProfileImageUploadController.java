package com.tikitaka.bidwinback.upload.presentation;

import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.config.OpenApiConfig;
import com.tikitaka.bidwinback.upload.application.ProfileImagePresignService;
import com.tikitaka.bidwinback.upload.presentation.dto.request.ProfileImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.response.ProfileImagePresignResponse;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/uploads/profile-images")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
@Tag(name = "이미지 업로드", description = "S3 직접 업로드용 Presigned URL 발급")
public class ProfileImageUploadController {

    private final ProfileImagePresignService presignService;

    @Operation(
            summary = "프로필 이미지 업로드 URL 발급",
            description = "이미지 형식과 5MB 크기 제한을 검증하고 S3 PUT용 Presigned URL을 발급합니다. 업로드 뒤 objectKey로 프로필 이미지를 변경합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Presigned URL 발급 완료",
            useReturnTypeSchema = true
    )
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<ProfileImagePresignResponse>> presign(
            @Login AuthMember authMember,
            @Valid @RequestBody ProfileImagePresignRequest request
    ) {
        ProfileImagePresignResponse response = presignService.issue(
                authMember.memberId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
