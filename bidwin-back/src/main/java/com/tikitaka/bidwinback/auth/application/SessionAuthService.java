package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.member.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 세션에 담긴 인증 스냅샷이 현재도 유효한 자격인지 판단한다.
 * 인증 필터가 회원 서비스 전체에 의존하지 않도록 인증에 필요한 질의만 노출한다.
 */
@Service
@RequiredArgsConstructor
public class SessionAuthService {

    private final MemberService memberService;

    public boolean isAuthenticatable(Long memberId, long authVersion) {
        if (memberId == null) {
            return false;
        }

        return memberService.isActiveWithAuthVersion(memberId, authVersion);
    }
}
