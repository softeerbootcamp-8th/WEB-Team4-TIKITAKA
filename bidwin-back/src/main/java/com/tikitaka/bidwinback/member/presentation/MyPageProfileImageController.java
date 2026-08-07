package com.tikitaka.bidwinback.member.presentation;

import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.member.application.MemberProfileImageService;
import com.tikitaka.bidwinback.member.presentation.dto.request.ProfileImageUpdateRequest;
import com.tikitaka.bidwinback.member.presentation.dto.response.ProfileImageUpdateResponse;
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
public class MyPageProfileImageController {

    private final MemberProfileImageService profileImageService;

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
