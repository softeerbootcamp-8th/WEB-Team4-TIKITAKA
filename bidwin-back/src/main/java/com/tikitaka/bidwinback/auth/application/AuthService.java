package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.dto.LoginRequest;
import com.tikitaka.bidwinback.dto.SignUpRequest;
import com.tikitaka.bidwinback.dto.SignUpResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.application.MemberService;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberService memberService;
    private final PasswordHasher passwordHasher;

    public SignUpResponse signup(SignUpRequest request) {
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
}
