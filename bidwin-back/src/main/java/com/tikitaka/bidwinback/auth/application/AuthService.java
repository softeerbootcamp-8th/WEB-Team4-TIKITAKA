package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.dto.AvailabilityResponse;
import com.tikitaka.bidwinback.dto.EmailAvailabilityRequest;
import com.tikitaka.bidwinback.dto.NicknameAvailabilityRequest;
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
}
