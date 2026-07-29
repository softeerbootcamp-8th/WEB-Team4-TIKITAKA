package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.application.emailverification.EmailVerificationTokenService;
import com.tikitaka.bidwinback.auth.application.passwordreset.PasswordResetTokenService;
import com.tikitaka.bidwinback.auth.domain.enums.MailPurpose;
import com.tikitaka.bidwinback.auth.presentation.dto.response.AvailabilityResponse;
import com.tikitaka.bidwinback.auth.presentation.dto.request.EmailAvailabilityRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.EmailVerificationRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.EmailVerificationSendRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.LoginRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.NicknameAvailabilityRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.PasswordChangeRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.PasswordResetRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.SignUpRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.response.SignUpResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.application.MemberService;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberService memberService;
    private final PasswordHasher passwordHasher;
    private final PasswordResetTokenService passwordResetTokenService;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final TokenMailSender tokenMailSender;

    public AvailabilityResponse checkEmailAvailability(EmailAvailabilityRequest request) {
        memberService.validateEmailAvailable(request.email());
        return new AvailabilityResponse(true);
    }

    public AvailabilityResponse checkNicknameAvailability(NicknameAvailabilityRequest request) {
        memberService.validateNicknameAvailable(request.nickname());
        return new AvailabilityResponse(true);
    }

    public SignUpResponse signup(SignUpRequest request) {
        memberService.validateEmailAvailable(request.email());
        memberService.validateNicknameAvailable(request.nickname());

        String encodedPassword = passwordHasher.hash(request.password());
        Member member = memberService.create(
                request.email(),
                encodedPassword,
                request.name(),
                request.phoneNumber(),
                request.nickname()
        );

        return SignUpResponse.from(member);
    }

    public void sendVerificationEmail(EmailVerificationSendRequest request) {
        Member member = memberService.findByEmail(request.email())
                .orElseThrow(() -> new AuthException(ErrorCode.MEMBER_NOT_FOUND));

        String rawToken = emailVerificationTokenService.issue(member);
        tokenMailSender.send(MailPurpose.EMAIL_VERIFICATION, member.getEmail(), rawToken);
    }

    public void verifyEmail(EmailVerificationRequest request) {
        emailVerificationTokenService.verify(request.token());
    }

    public AuthMember login(LoginRequest request) {
        Member member = memberService.findByEmail(request.email())
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

        String encodedPassword = member.getPassword();
        boolean passwordMatches = passwordHasher.matches(
                request.password(),
                encodedPassword
        );

        if (!passwordMatches || member.getStatus() != MemberStatus.ACTIVE) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        return AuthMember.from(member);
    }

    public void requestPasswordReset(PasswordResetRequest request) {
        // 회원 존재 여부가 응답으로 노출되지 않도록 미가입·비활성 회원은 동일하게 처리한다.
        memberService.findByEmail(request.email())
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .ifPresent(member -> {
                    String rawToken = passwordResetTokenService.issue(member);
                    tokenMailSender.send(MailPurpose.PASSWORD_RESET, member.getEmail(), rawToken);
                });
    }

    public void resetPassword(PasswordChangeRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new AuthException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }

        passwordResetTokenService.resetPassword(request.token(), request.newPassword());
    }
}
