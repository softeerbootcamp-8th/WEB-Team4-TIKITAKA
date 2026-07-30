package com.tikitaka.bidwinback.upload.domain.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PendingAuctionImageTest {

    @Test
    void 회원과_draftId에_속한_PENDING_이미지를_발급한다() {
        UUID draftId = UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09");

        PendingAuctionImage image = PendingAuctionImage.issue(
                1L,
                draftId,
                "auction-images/image-id.jpg"
        );

        assertThat(image.getMemberId()).isEqualTo(1L);
        assertThat(image.getDraftId()).isEqualTo(draftId);
        assertThat(image.getObjectKey()).isEqualTo("auction-images/image-id.jpg");
    }
}
