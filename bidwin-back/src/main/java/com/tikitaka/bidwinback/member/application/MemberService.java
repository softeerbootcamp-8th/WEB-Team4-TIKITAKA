package com.tikitaka.bidwinback.member.application;

import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.DUPLICATE_EMAIL;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.DUPLICATE_NICKNAME;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.EMAIL_VERIFICATION_PENDING;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_ACTIVE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_FOUND;
import static com.tikitaka.bidwinback.member.domain.entity.Member.EMAIL_UNIQUE_CONSTRAINT;
import static com.tikitaka.bidwinback.member.domain.entity.Member.NICKNAME_UNIQUE_CONSTRAINT;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public void validateEmailAvailable(String email) {
        memberRepository.findByEmail(email).ifPresent(member -> {
            if (member.getStatus() == MemberStatus.PENDING) {
                throw new MemberException(EMAIL_VERIFICATION_PENDING);
            }
            throw new MemberException(DUPLICATE_EMAIL);
        });
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

    @Transactional
    public String changeNickname(Long memberId, String nickname) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MEMBER_NOT_FOUND));

        if (member.getNickname().equals(nickname)) {
            return nickname;
        }

        member.changeNickname(nickname);
        try {
            // UPDATE의 UNIQUE 제약 위반도 서비스 호출 안에서 응답으로 변환한다.
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateDataIntegrityViolation(exception);
        }
        return member.getNickname();
    }

    // 알려진 UNIQUE 제약 위반만 회원 중복 예외로 변환한다.
    private RuntimeException translateDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        String constraintName = extractConstraintName(exception);
        if (matchesConstraint(constraintName, EMAIL_UNIQUE_CONSTRAINT)) {
            return new MemberException(DUPLICATE_EMAIL);
        }
        if (matchesConstraint(constraintName, NICKNAME_UNIQUE_CONSTRAINT)) {
            return new MemberException(DUPLICATE_NICKNAME);
        }
        return exception;
    }

    private boolean matchesConstraint(String actualName, String expectedName) {
        if (actualName == null) {
            return false;
        }

        int separatorIndex = actualName.lastIndexOf('.');
        String unqualifiedName = actualName.substring(separatorIndex + 1);
        return expectedName.equalsIgnoreCase(unqualifiedName);
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

    @Transactional(readOnly = true)
    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    @Transactional
    public void activateByEmail(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberException(MEMBER_NOT_FOUND));
        if (member.getStatus() == MemberStatus.ACTIVE) {
            return;
        }
        if (member.getStatus() != MemberStatus.PENDING) {
            throw new MemberException(
                    MEMBER_NOT_ACTIVE,
                    "이메일 인증 우회 대상이 아닌 회원입니다."
            );
        }
        member.activate();
    }

    @Transactional(readOnly = true)
    public boolean isActiveWithAuthVersion(Long memberId, long authVersion) {
        return memberRepository.existsByIdAndStatusAndAuthVersion(
                memberId,
                MemberStatus.ACTIVE,
                authVersion
        );
    }
}
