package com.tikitaka.bidwinback.member.application;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hibernate.exception.ConstraintViolationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    @Test
    void 이메일_유니크_제약을_위반하면_중복_이메일_예외로_변환한다() {
        DataIntegrityViolationException exception =
                createConstraintViolation(Member.EMAIL_UNIQUE_CONSTRAINT);
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(exception);

        assertThatExceptionOfType(MemberException.class)
                .isThrownBy(this::createMember)
                .extracting(MemberException::getErrorCode)
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void 테이블명이_포함된_이메일_유니크_제약도_중복_이메일_예외로_변환한다() {
        DataIntegrityViolationException exception =
                createConstraintViolation("member." + Member.EMAIL_UNIQUE_CONSTRAINT);
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(exception);

        assertThatExceptionOfType(MemberException.class)
                .isThrownBy(this::createMember)
                .extracting(MemberException::getErrorCode)
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void 닉네임_유니크_제약을_위반하면_중복_닉네임_예외로_변환한다() {
        DataIntegrityViolationException exception =
                createConstraintViolation(Member.NICKNAME_UNIQUE_CONSTRAINT);
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(exception);

        assertThatExceptionOfType(MemberException.class)
                .isThrownBy(this::createMember)
                .extracting(MemberException::getErrorCode)
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    void 알_수_없는_데이터_무결성_예외는_그대로_전파한다() {
        DataIntegrityViolationException exception =
                createConstraintViolation("uk_member_unknown");
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(exception);

        assertThatThrownBy(this::createMember).isSameAs(exception);
    }

    private DataIntegrityViolationException createConstraintViolation(String constraintName) {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn(constraintName);
        return new DataIntegrityViolationException("데이터 무결성 제약 위반", cause);
    }

    private Member createMember() {
        return memberService.create(
                "member@example.com",
                "encoded-password",
                "홍길동",
                "01012345678",
                "티키타카"
        );
    }
}
