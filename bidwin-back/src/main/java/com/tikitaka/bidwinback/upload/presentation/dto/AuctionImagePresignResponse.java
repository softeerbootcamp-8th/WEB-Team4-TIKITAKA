package com.tikitaka.bidwinback.upload.presentation.dto;

import java.time.Instant;

public record AuctionImagePresignResponse(
        String presignedUrl,
        String objectKey,
        Instant expiresAt
) {
}
