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
import org.springframework.dao.DataAccessException;
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

        // 이 호출이 반환된 시점엔 비밀번호 변경이 이미 DB에 커밋되어 있다.
        AuthMember refreshedAuth = passwordChangeService.change(
                authMember,
                request.currentPassword(),
                request.newPassword(),
                request.newPasswordConfirm()
        );

        try {
            servletRequest.changeSessionId();
            session.setAttribute(AuthConstant.SESSION_KEY, refreshedAuth);
        } catch (DataAccessException exception) {
            // 비밀번호 변경 자체는 이미 성공(커밋)했다. 이 세션만 새 authVersion을
            // 반영하지 못해 예전 버전을 그대로 들고 있게 되므로, 다음 인증 요청까지
            // 기다리지 않고 지금 바로 재로그인하도록 안내한다(다른 기기 세션도
            // 이미 예전 버전인 채로 같은 방식으로 거부된다).
            throw new AuthException(ErrorCode.UNAUTHENTICATED);
        }

        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
