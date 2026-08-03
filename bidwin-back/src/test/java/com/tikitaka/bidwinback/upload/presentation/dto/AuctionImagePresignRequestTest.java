package com.tikitaka.bidwinback.upload.presentation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionImagePresignRequestTest {

    private static final long MAX_FILE_SIZE = 10_485_760L;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 올바른_이미지_업로드_요청은_검증을_통과한다() {
        AuctionImagePresignRequest request = validRequest(MAX_FILE_SIZE);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void 파일명이_비어있으면_검증에_실패한다() {
        AuctionImagePresignRequest request =
                new AuctionImagePresignRequest(" ", "image/jpeg", 1L);

        assertViolation(request, "fileName");
    }

    @Test
    void 파일명이_255자를_초과하면_검증에_실패한다() {
        AuctionImagePresignRequest request =
                new AuctionImagePresignRequest("a".repeat(256), "image/jpeg", 1L);

        assertViolation(request, "fileName");
    }

    @Test
    void 파일_형식이_비어있으면_검증에_실패한다() {
        AuctionImagePresignRequest request =
                new AuctionImagePresignRequest("image.jpg", " ", 1L);

        assertViolation(request, "contentType");
    }

    @Test
    void 파일_형식이_100자를_초과하면_검증에_실패한다() {
        AuctionImagePresignRequest request =
                new AuctionImagePresignRequest("image.jpg", "a".repeat(101), 1L);

        assertViolation(request, "contentType");
    }

    @Test
    void 파일_크기가_누락되면_검증에_실패한다() {
        AuctionImagePresignRequest request =
                new AuctionImagePresignRequest("image.jpg", "image/jpeg", null);

        assertViolation(request, "size");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L})
    void 파일_크기가_0_이하이면_검증에_실패한다(long size) {
        AuctionImagePresignRequest request = validRequest(size);

        assertViolation(request, "size");
    }

    @Test
    void 파일_크기가_10MB를_초과하면_검증에_실패한다() {
        AuctionImagePresignRequest request = validRequest(MAX_FILE_SIZE + 1);

        assertViolation(request, "size");
    }

    private AuctionImagePresignRequest validRequest(Long size) {
        return new AuctionImagePresignRequest("image.jpg", "image/jpeg", size);
    }

    private void assertViolation(AuctionImagePresignRequest request, String field) {
        Set<ConstraintViolation<AuctionImagePresignRequest>> violations =
                validator.validate(request);

        assertTrue(violations.stream()
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals(field)));
    }
}
