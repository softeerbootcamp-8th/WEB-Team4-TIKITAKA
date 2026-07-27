package com.tikitaka.bidwinback.auth.presentation.dto.response;

import com.tikitaka.bidwinback.member.domain.entity.Member;

public record SignUpResponse(Long memberId, String email, String nickname) {

    public static SignUpResponse from(Member member) {
        return new SignUpResponse(member.getId(), member.getEmail(), member.getNickname());
    }
}
