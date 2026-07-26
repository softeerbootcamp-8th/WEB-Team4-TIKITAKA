package com.tikitaka.bidwinback.member.application;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository);
    }

    @Test
    void 존재하지_않는_이메일은_사용할_수_있다() {
        String email = "member@example.com";
        when(memberRepository.existsByEmail(email)).thenReturn(false);

        assertThatCode(() -> memberService.validateEmailAvailable(email))
                .doesNotThrowAnyException();
    }

    @Test
    void 이미_존재하는_이메일이면_중복_예외를_던진다() {
        String email = "member@example.com";
        when(memberRepository.existsByEmail(email)).thenReturn(true);

        assertThatExceptionOfType(MemberException.class)
                .isThrownBy(() -> memberService.validateEmailAvailable(email))
                .extracting(MemberException::getErrorCode)
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void 존재하지_않는_닉네임은_사용할_수_있다() {
        String nickname = "티키타카";
        when(memberRepository.existsByNickname(nickname)).thenReturn(false);

        assertThatCode(() -> memberService.validateNicknameAvailable(nickname))
                .doesNotThrowAnyException();
    }

    @Test
    void 이미_존재하는_닉네임이면_중복_예외를_던진다() {
        String nickname = "티키타카";
        when(memberRepository.existsByNickname(nickname)).thenReturn(true);

        assertThatExceptionOfType(MemberException.class)
                .isThrownBy(() -> memberService.validateNicknameAvailable(nickname))
                .extracting(MemberException::getErrorCode)
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }
}
