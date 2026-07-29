package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.member.application.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAuthServiceTest {

    @Mock
    private MemberService memberService;

    @Test
    void 회원이_활성_상태이고_인증_버전이_일치하면_인증할_수_있다() {
        // given
        SessionAuthService sessionAuthService = new SessionAuthService(memberService);
        when(memberService.isActiveWithAuthVersion(1L, 3L)).thenReturn(true);

        // when
        boolean authenticatable = sessionAuthService.isAuthenticatable(1L, 3L);

        // then
        assertThat(authenticatable).isTrue();
    }

    @Test
    void 활성_상태와_인증_버전이_일치하지_않으면_인증할_수_없다() {
        // given
        SessionAuthService sessionAuthService = new SessionAuthService(memberService);
        when(memberService.isActiveWithAuthVersion(1L, 3L)).thenReturn(false);

        // when
        boolean authenticatable = sessionAuthService.isAuthenticatable(1L, 3L);

        // then
        assertThat(authenticatable).isFalse();
    }

    @Test
    void 회원_식별자가_없으면_회원을_조회하지_않고_인증할_수_없다() {
        // given
        SessionAuthService sessionAuthService = new SessionAuthService(memberService);

        // when
        boolean authenticatable = sessionAuthService.isAuthenticatable(null, 0L);

        // then
        assertThat(authenticatable).isFalse();
        verifyNoInteractions(memberService);
    }
}
