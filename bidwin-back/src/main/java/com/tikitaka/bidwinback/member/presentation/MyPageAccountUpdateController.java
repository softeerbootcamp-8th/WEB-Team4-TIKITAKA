package com.tikitaka.bidwinback.member.presentation;

import com.tikitaka.bidwinback.auth.application.AuthenticatedPasswordChangeService;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.application.MemberService;
import com.tikitaka.bidwinback.member.presentation.dto.request.NicknameUpdateRequest;
import com.tikitaka.bidwinback.member.presentation.dto.request.PasswordUpdateRequest;
import com.tikitaka.bidwinback.member.presentation.dto.response.NicknameUpdateResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mypage")
public class MyPageAccountUpdateController {

    private final MemberService memberService;
    private final AuthenticatedPasswordChangeService passwordChangeService;

    @PatchMapping("/nickname")
    public ResponseEntity<ApiResponse<NicknameUpdateResponse>> changeNickname(
            @Login AuthMember authMember,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        String nickname = memberService.changeNickname(
                authMember.memberId(),
                request.nickname()
        );
        return ResponseEntity.ok(ApiResponse.success(
                new NicknameUpdateResponse(nickname)
        ));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Login AuthMember authMember,
            @Valid @RequestBody PasswordUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        HttpSession session = servletRequest.getSession(false);
        if (session == null) {
            throw new AuthException(ErrorCode.UNAUTHENTICATED);
        }

        AuthMember refreshedAuth = passwordChangeService.change(
                authMember,
                request.currentPassword(),
                request.newPassword(),
                request.newPasswordConfirm()
        );

        servletRequest.changeSessionId();
        session.setAttribute(AuthConstant.SESSION_KEY, refreshedAuth);

        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
