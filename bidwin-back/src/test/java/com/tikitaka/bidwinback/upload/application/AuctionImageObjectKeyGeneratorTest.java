package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.upload.domain.enums.AuctionImageFileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AuctionImageObjectKeyGeneratorTest {

    private final AuctionImageObjectKeyGenerator generator =
            new AuctionImageObjectKeyGenerator();

    @ParameterizedTest
    @CsvSource({
            "JPEG, jpg",
            "PNG, png",
            "WEBP, webp"
    })
    void 경매와_uploadId와_이미지_형식에_맞는_영구_objectKey를_생성한다(
            AuctionImageFileType fileType,
            String extension
    ) {
        UUID uploadId = UUID.fromString("a2ddf707-cc3b-43d0-8c92-b86e8da74bc6");
        String objectKey = generator.generatePermanent(100L, uploadId, fileType);

        assertEquals("auction-images/100/" + uploadId + "." + extension, objectKey);
    }

    @Test
    void 호출할_때마다_서로_다른_uploadId를_생성한다() {
        UUID first = generator.generateUploadId();
        UUID second = generator.generateUploadId();

        assertNotEquals(first, second);
    }

    @Test
    void uploadId를_포함한_임시_objectKey를_생성한다() {
        UUID uploadId = UUID.fromString("a2ddf707-cc3b-43d0-8c92-b86e8da74bc6");

        assertEquals("temp/" + uploadId, generator.generateTemporary(uploadId));
    }
}
