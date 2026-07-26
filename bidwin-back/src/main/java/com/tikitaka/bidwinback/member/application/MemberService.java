package com.tikitaka.bidwinback.member.application;

import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.DUPLICATE_EMAIL;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.DUPLICATE_NICKNAME;
import static com.tikitaka.bidwinback.member.domain.entity.Member.EMAIL_UNIQUE_CONSTRAINT;
import static com.tikitaka.bidwinback.member.domain.entity.Member.NICKNAME_UNIQUE_CONSTRAINT;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public void validateEmailAvailable(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new MemberException(DUPLICATE_EMAIL);
        }
    }

    @Transactional(readOnly = true)
    public void validateNicknameAvailable(String nickname) {
        if (memberRepository.existsByNickname(nickname)) {
            throw new MemberException(DUPLICATE_NICKNAME);
        }
    }

    @Transactional
    public Member create(
            String email,
            String encodedPassword,
            String name,
            String phoneNumber,
            String nickname
    ) {
        Member member = Member.builder()
                .email(email)
                .password(encodedPassword)
                .name(name)
                .phoneNumber(phoneNumber)
                .nickname(nickname)
                .build();

        try {
            // 트랜잭션 커밋 전에 UNIQUE 제약 위반을 감지하기 위해 즉시 flush한다.
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw translateDataIntegrityViolation(exception);
        }
    }

    // 알려진 UNIQUE 제약 위반만 회원 중복 예외로 변환한다.
    private RuntimeException translateDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        String constraintName = extractConstraintName(exception);
        if (EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraintName)) {
            return new MemberException(DUPLICATE_EMAIL);
        }
        if (NICKNAME_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraintName)) {
            return new MemberException(DUPLICATE_NICKNAME);
        }
        return exception;
    }

    // 중첩된 DB 예외에서 Hibernate가 추출한 제약조건 이름을 찾는다.
    private String extractConstraintName(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }
}
