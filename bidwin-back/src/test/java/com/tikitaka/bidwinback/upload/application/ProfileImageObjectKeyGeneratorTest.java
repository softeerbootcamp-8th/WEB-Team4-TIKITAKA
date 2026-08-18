package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.upload.domain.enums.ProfileImageFileType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileImageObjectKeyGeneratorTest {

    private final ProfileImageObjectKeyGenerator generator =
            new ProfileImageObjectKeyGenerator();

    @Test
    void 회원별_경로와_정규화된_확장자로_키를_생성한다() {
        String result = generator.generate(12L, ProfileImageFileType.JPEG);

        assertThat(result)
                .startsWith("profile-images/12/")
                .endsWith(".jpg")
                .hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void 회원_경로에_속한_키만_소유한_키로_판단한다() {
        assertThat(generator.belongsTo(1L, "profile-images/1/image.jpg")).isTrue();
        assertThat(generator.belongsTo(1L, "profile-images/10/image.jpg")).isFalse();
        assertThat(generator.belongsTo(1L, "auction-images/image.jpg")).isFalse();
    }
}
