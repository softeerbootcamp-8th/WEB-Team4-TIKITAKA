package com.tikitaka.bidwinback.upload.domain;

import java.util.UUID;

public record AuctionImageUploadReservation(
        UUID uploadId,
        String objectKey,
        String contentType,
        long contentLength,
        String checksumSha256
) {
}
