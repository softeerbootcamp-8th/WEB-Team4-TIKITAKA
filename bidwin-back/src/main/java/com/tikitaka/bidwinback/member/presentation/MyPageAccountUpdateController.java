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

        try {
            // 세션 갱신을 비밀번호 변경과 같은 트랜잭션 안에서 수행한다(콜백으로 전달).
            // 세션 갱신이 실패하면 이 예외가 트랜잭션을 롤백시켜, 비밀번호만 바뀌고
            // 세션은 예전 상태로 남는 부분 성공을 막는다.
            passwordChangeService.change(
                    authMember,
                    request.currentPassword(),
                    request.newPassword(),
                    request.newPasswordConfirm(),
                    refreshedAuth -> {
                        servletRequest.changeSessionId();
                        session.setAttribute(AuthConstant.SESSION_KEY, refreshedAuth);
                    }
            );
        } catch (DataAccessException exception) {
            // 세션 갱신 실패로 비밀번호 변경 자체도 함께 롤백됐으므로, 재시도하면
            // 현재(원래) 비밀번호로 다시 시도하게 된다 - 부분 성공 상태가 아니다.
            throw new AuthException(ErrorCode.AUTHENTICATION_UNAVAILABLE);
        }

        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
