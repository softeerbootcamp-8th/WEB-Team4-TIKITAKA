package com.tikitaka.bidwinback.upload.domain.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PendingAuctionImageTest {

    @Test
    void 회원과_draftId에_속한_PENDING_이미지를_발급한다() {
        UUID draftId = UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09");
        UUID uploadId = UUID.fromString("a2ddf707-cc3b-43d0-8c92-b86e8da74bc6");

        PendingAuctionImage image = PendingAuctionImage.issue(
                1L,
                draftId,
                uploadId,
                "temp/a2ddf707-cc3b-43d0-8c92-b86e8da74bc6",
                "image/jpeg",
                248_392L,
                "mH2LpXfVw2f2Y87a5SIc1J5m3eS74iqrAx/CBEhb3c4="
        );

        assertThat(image.getMemberId()).isEqualTo(1L);
        assertThat(image.getDraftId()).isEqualTo(draftId);
        assertThat(image.getUploadId()).isEqualTo(uploadId);
        assertThat(image.getObjectKey())
                .isEqualTo("temp/a2ddf707-cc3b-43d0-8c92-b86e8da74bc6");
        assertThat(image.getContentType()).isEqualTo("image/jpeg");
        assertThat(image.getContentLength()).isEqualTo(248_392L);
    }
}
