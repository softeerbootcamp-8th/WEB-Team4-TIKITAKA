package com.tikitaka.bidwinback.upload.domain.enums;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.upload.domain.exception.UploadException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ProfileImageFileTypeTest {

    @Test
    void 파일명과_MIME_타입이_일치하는_이미지_형식을_반환한다() {
        ProfileImageFileType result = ProfileImageFileType.from(
                "PROFILE.JPEG",
                "image/jpeg"
        );

        assertThat(result).isEqualTo(ProfileImageFileType.JPEG);
        assertThat(result.getObjectExtension()).isEqualTo("jpg");
    }

    @Test
    void 파일_확장자와_MIME_타입이_다르면_거절한다() {
        assertThatExceptionOfType(UploadException.class)
                .isThrownBy(() -> ProfileImageFileType.from("profile.png", "image/jpeg"))
                .extracting(UploadException::getErrorCode)
                .isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    void SVG_이미지는_허용하지_않는다() {
        assertThatExceptionOfType(UploadException.class)
                .isThrownBy(() -> ProfileImageFileType.from("profile.svg", "image/svg+xml"))
                .extracting(UploadException::getErrorCode)
                .isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }
}
