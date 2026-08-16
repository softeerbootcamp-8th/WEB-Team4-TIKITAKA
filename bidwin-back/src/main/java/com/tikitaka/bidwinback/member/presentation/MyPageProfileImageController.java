package com.tikitaka.bidwinback.member.presentation;

import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.config.OpenApiConfig;
import com.tikitaka.bidwinback.member.application.MemberProfileImageService;
import com.tikitaka.bidwinback.member.presentation.dto.request.ProfileImageUpdateRequest;
import com.tikitaka.bidwinback.member.presentation.dto.response.ProfileImageUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mypage/profile-image")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
@Tag(name = "마이페이지", description = "내 정보와 활동 내역 관리")
public class MyPageProfileImageController {

    private final MemberProfileImageService profileImageService;

    @Operation(summary = "프로필 이미지 변경", description = "본인이 업로드한 프로필 이미지 object key를 현재 프로필로 확정합니다.")
    @PatchMapping
    public ResponseEntity<ApiResponse<ProfileImageUpdateResponse>> change(
            @Login AuthMember authMember,
            @Valid @RequestBody ProfileImageUpdateRequest request
    ) {
        ProfileImageUpdateResponse response = profileImageService.change(
                authMember.memberId(),
                request.objectKey()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "프로필 이미지 초기화", description = "현재 프로필 이미지를 기본 이미지로 되돌립니다.")
    @DeleteMapping
    public ResponseEntity<ApiResponse<ProfileImageUpdateResponse>> reset(
            @Login AuthMember authMember
    ) {
        ProfileImageUpdateResponse response = profileImageService.reset(
                authMember.memberId()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
