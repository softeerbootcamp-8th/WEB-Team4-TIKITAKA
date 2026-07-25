package com.tikitaka.bidwinback.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SignUpRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 올바른_회원가입_요청은_검증을_통과한다() {
        SignUpRequest request = new SignUpRequest(
                "member@example.com",
                "password!",
                "홍길동",
                "01012345678",
                "티키타카"
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void 이메일_형식이_올바르지_않으면_검증에_실패한다() {
        SignUpRequest request = new SignUpRequest(
                "invalid-email",
                "password!",
                "홍길동",
                "01012345678",
                "티키타카"
        );

        assertViolation(request, "email");
    }

    @Test
    void 비밀번호가_정책에_맞지_않으면_검증에_실패한다() {
        SignUpRequest request = new SignUpRequest(
                "member@example.com",
                "password",
                "홍길동",
                "01012345678",
                "티키타카"
        );

        assertViolation(request, "password");
    }

    @Test
    void 닉네임이_비어있으면_검증에_실패한다() {
        SignUpRequest request = new SignUpRequest(
                "member@example.com",
                "password!",
                "홍길동",
                "01012345678",
                " "
        );

        assertViolation(request, "nickname");
    }

    @Test
    void 이름이_비어있으면_검증에_실패한다() {
        SignUpRequest request = new SignUpRequest(
                "member@example.com",
                "password!",
                " ",
                "01012345678",
                "티키타카"
        );

        assertViolation(request, "name");
    }

    @Test
    void 전화번호_형식이_올바르지_않으면_검증에_실패한다() {
        SignUpRequest request = new SignUpRequest(
                "member@example.com",
                "password!",
                "홍길동",
                "010-1234-5678",
                "티키타카"
        );

        assertViolation(request, "phoneNumber");
    }

    private void assertViolation(SignUpRequest request, String field) {
        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);
        assertTrue(violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(field)));
    }
}
