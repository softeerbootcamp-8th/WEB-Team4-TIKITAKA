package com.tikitaka.bidwinback.mypage.presentation;

import com.tikitaka.bidwinback.auth.application.AuthenticatedPasswordChangeService;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.config.OpenApiConfig;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.application.MemberService;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.mypage.presentation.dto.request.NicknameUpdateRequest;
import com.tikitaka.bidwinback.mypage.presentation.dto.request.PasswordUpdateRequest;
import com.tikitaka.bidwinback.mypage.presentation.dto.request.PointChargeRequest;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.DepositResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.NicknameUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/mypage")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
@Tag(name = "마이페이지", description = "내 정보와 활동 내역 관리")
public class MyPageAccountUpdateController {

    private final MemberService memberService;
    private final AuthenticatedPasswordChangeService passwordChangeService;

    @Operation(summary = "닉네임 변경", description = "중복 여부를 확인한 뒤 로그인 회원의 닉네임을 변경합니다.")
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

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인하고 새 비밀번호로 변경한 뒤 세션의 인증 정보를 갱신합니다.")
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
            // 비밀번호 변경 자체는 이미 성공(커밋)했다. changeSessionId()는 세션ID를 이미
            // 바꿔놨고 IMMEDIATE flush라 다음 세션 쓰기에서 그 rename이 Redis에 반영될 수
            // 있는데, 그 직후 이 catch로 빠졌다는 건 뒤이은 setAttribute()가 실패했다는
            // 뜻이다. SessionRepositoryFilter의 최종 커밋이 이 반쯤 바뀐 세션을 다시 저장
            // 시도하지 않도록 지금 명시적으로 폐기하고, 예전 버전을 그대로 들고 있던
            // 세션이 아니라 재로그인이 필요한 상태임을 바로 알린다.
            try {
                session.invalidate();
            } catch (IllegalStateException | DataAccessException ignored) {
            }
            log.atError()
                    .addKeyValue("event", "password_session_refresh_failed")
                    .addKeyValue("memberId", authMember.memberId())
                    .addKeyValue("failureType", exception.getClass().getSimpleName())
                    .log("비밀번호 변경 후 세션 인증 정보를 갱신하지 못했습니다.");
            throw new AuthException(ErrorCode.UNAUTHENTICATED);
        }

        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @Operation(summary = "포인트 충전(테스트용)", description = "실제 결제 없이 로그인 회원의 보증금 잔액을 바로 충전합니다.")
    @PostMapping("/points/charge")
    public ResponseEntity<ApiResponse<DepositResponse>> chargePoint(
            @Login AuthMember authMember,
            @Valid @RequestBody PointChargeRequest request
    ) {
        Member member = memberService.chargePoint(authMember.memberId(), request.amount());
        return ResponseEntity.ok(ApiResponse.success(
                new DepositResponse(member.getTotalPoint(), member.getLockedPoint())
        ));
    }
}
