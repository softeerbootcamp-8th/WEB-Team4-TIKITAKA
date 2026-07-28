package com.tikitaka.bidwinback.upload.domain;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuctionImageFileTypeTest {

    @ParameterizedTest
    @CsvSource({
            "image.jpg, image/jpeg, JPEG",
            "image.jpeg, image/jpeg, JPEG",
            "image.PNG, image/png, PNG",
            "image.webp, image/webp, WEBP"
    })
    void 파일명과_콘텐츠_타입으로_이미지_형식을_찾는다(
            String fileName,
            String contentType,
            AuctionImageFileType expected
    ) {
        AuctionImageFileType result =
                AuctionImageFileType.from(fileName, contentType);

        assertEquals(expected, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image", "image.", "image.gif"})
    void 지원하지_않는_확장자는_예외가_발생한다(String fileName) {
        UploadException exception = assertThrows(
                UploadException.class,
                () -> AuctionImageFileType.from(fileName, "image/jpeg")
        );

        assertEquals(ErrorCode.UNSUPPORTED_IMAGE_TYPE, exception.getErrorCode());
    }

    @Test
    void 확장자와_콘텐츠_타입이_일치하지_않으면_예외가_발생한다() {
        UploadException exception = assertThrows(
                UploadException.class,
                () -> AuctionImageFileType.from("image.jpg", "image/png")
        );

        assertEquals(ErrorCode.UNSUPPORTED_IMAGE_TYPE, exception.getErrorCode());
    }
}
