package com.tikitaka.bidwinback.global.auth;

import com.tikitaka.bidwinback.member.domain.entity.Member;

/**
 * 세션에 저장되는 최소 인증 정보. 엔티티를 세션에 넣지 않기 위한 스냅샷이므로
 * 변경 가능한 회원 정보(포인트, 상태 등)는 담지 않는다.
 */
public record AuthMember(
        Long memberId,
        String nickname
) {

    public static AuthMember from(Member member) {
        return new AuthMember(member.getId(), member.getNickname());
    }
}
