package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.upload.domain.AuctionImageFileType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionImageObjectKeyGeneratorTest {

    private final AuctionImageObjectKeyGenerator generator =
            new AuctionImageObjectKeyGenerator();

    @ParameterizedTest
    @CsvSource({
            "JPEG, jpg",
            "PNG, png",
            "WEBP, webp"
    })
    void 이미지_형식에_맞는_objectKey를_생성한다(
            AuctionImageFileType fileType,
            String extension
    ) {
        String objectKey = generator.generate(fileType);

        assertTrue(objectKey.matches(
                "^auction-images/[0-9a-f]{8}-[0-9a-f]{4}-"
                        + "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\."
                        + extension + "$"
        ));
    }

    @Test
    void 호출할_때마다_서로_다른_objectKey를_생성한다() {
        String first = generator.generate(AuctionImageFileType.JPEG);
        String second = generator.generate(AuctionImageFileType.JPEG);

        assertNotEquals(first, second);
    }
}
