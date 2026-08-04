package com.tikitaka.bidwinback.member.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    void 새_회원의_인증_버전은_0이다() {
        // given
        Member member = createMember();

        // when
        long authVersion = member.getAuthVersion();

        // then
        assertThat(authVersion).isZero();
    }

    @Test
    void 비밀번호를_변경할_때마다_인증_버전이_증가한다() {
        // given
        Member member = createMember();

        // when
        member.changePassword("encoded-first-password");
        member.changePassword("encoded-second-password");

        // then
        assertThat(member.getAuthVersion()).isEqualTo(2L);
    }

    private Member createMember() {
        return Member.builder()
                .email("member@example.com")
                .password("encoded-password")
                .name("홍길동")
                .phoneNumber("01012345678")
                .nickname("티키타카")
                .build();
    }
}
