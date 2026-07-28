package com.tikitaka.bidwinback.upload.presentation.dto;

import java.time.Instant;

public record AuctionImagePresignResponse(
        String uploadUrl,
        String objectKey,
        Instant expiresAt
) {
}
