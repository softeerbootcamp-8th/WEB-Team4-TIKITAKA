package com.tikitaka.bidwinback.member.presentation;

import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.config.OpenApiConfig;
import com.tikitaka.bidwinback.member.application.MyPageService;
import com.tikitaka.bidwinback.member.presentation.dto.response.MyPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mypage")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
@Tag(name = "마이페이지", description = "내 정보와 활동 내역 관리")
public class MyPageController {

    private final MyPageService myPageService;

    @Operation(summary = "내 정보 조회", description = "로그인 회원의 프로필과 보증금·거래 요약 정보를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<MyPageResponse>> getMyPage(@Login AuthMember authMember) {
        MyPageResponse response = myPageService.getMyPage(authMember.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
