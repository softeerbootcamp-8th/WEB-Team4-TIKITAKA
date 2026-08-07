package com.tikitaka.bidwinback.upload.presentation;

import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.upload.application.ProfileImagePresignService;
import com.tikitaka.bidwinback.upload.presentation.dto.ProfileImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.ProfileImagePresignResponse;
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
public class ProfileImageUploadController {

    private final ProfileImagePresignService presignService;

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
