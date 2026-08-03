package com.tikitaka.bidwinback.upload.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AuctionImagePresignResponse(
        String presignedUrl,
        String objectKey,
        Map<String, List<String>> signedHeaders,
        Instant expiresAt
) {
}
