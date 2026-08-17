package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.application.emailverification.EmailVerificationIssueResult;
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
import com.tikitaka.bidwinback.auth.presentation.dto.response.EmailVerificationSendResponse;
import com.tikitaka.bidwinback.auth.presentation.dto.response.SignUpResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.config.MailRateLimitProperties;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.application.MemberService;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberService memberService;
    private final PasswordHasher passwordHasher;
    private final PasswordResetTokenService passwordResetTokenService;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final Clock clock;
    private final TokenMailDispatcher tokenMailDispatcher;
    private final MailRateLimitProperties mailRateLimitProperties;

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

    public EmailVerificationSendResponse sendVerificationEmail(EmailVerificationSendRequest request) {
        // 회원 존재 여부가 응답으로 노출되지 않도록 미가입·PENDING이 아닌 회원도 발송한 것과 같게 응답한다.
        return memberService.findByEmail(request.email())
                .filter(member -> member.getStatus() == MemberStatus.PENDING)
                .map(this::issueAndSendVerificationMail)
                .orElseGet(() -> EmailVerificationSendResponse.of(
                        true,
                        mailRateLimitProperties.cooldown()
                ));
    }

    private EmailVerificationSendResponse issueAndSendVerificationMail(Member member) {
        EmailVerificationIssueResult issueResult = emailVerificationTokenService.issue(member);
        if (!issueResult.isIssued()) {
            // 재전송 제한에 걸린 요청은 남은 대기 시간만 알려주고 메일을 보내지 않는다.
            return EmailVerificationSendResponse.of(false, issueResult.retryAfter());
        }

        tokenMailDispatcher.send(
                MailPurpose.EMAIL_VERIFICATION,
                member.getEmail(),
                issueResult.rawToken()
        );
        return EmailVerificationSendResponse.of(true, issueResult.retryAfter());
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

        return AuthMember.from(member, clock.instant());
    }

    public void requestPasswordReset(PasswordResetRequest request) {
        // 회원 존재 여부가 응답으로 노출되지 않도록 미가입·비활성 회원은 동일하게 처리한다.
        memberService.findByEmail(request.email())
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .ifPresent(member -> {
                    passwordResetTokenService.issue(member)
                            .ifPresent(rawToken -> tokenMailDispatcher.send(
                                    MailPurpose.PASSWORD_RESET,
                                    member.getEmail(),
                                    rawToken
                            ));
                });
    }

    public void resetPassword(PasswordChangeRequest request) {
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new AuthException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }

        passwordResetTokenService.resetPassword(request.token(), request.newPassword());
    }
}
