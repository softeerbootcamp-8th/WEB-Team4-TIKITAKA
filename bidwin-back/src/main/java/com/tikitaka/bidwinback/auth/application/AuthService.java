package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.dto.SignUpRequest;
import com.tikitaka.bidwinback.dto.SignUpResponse;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
