package com.tikitaka.bidwinback.global.auth;

import com.tikitaka.bidwinback.member.domain.entity.Member;

import java.time.Instant;

/**
 * 세션에 저장되는 최소 인증 스냅샷. 엔티티나 포인트/상태 등은 담지 않고,
 * 현재 자격과 비교할 로그인 당시 authVersion만 보관한다.
 * loggedInAt은 활동할 때마다 갱신하지 않는 절대 만료의 기준 시각이다.
 * authVersion과 loggedInAt이 생략된 스냅샷은 검증을 우회하므로 편의 생성자를 두지 않는다.
 */
public record AuthMember(
        Long memberId,
        long authVersion,
        Instant loggedInAt
) {

    public static AuthMember from(Member member, Instant loggedInAt) {
        return new AuthMember(
                member.getId(),
                member.getAuthVersion(),
                loggedInAt
        );
    }
}
