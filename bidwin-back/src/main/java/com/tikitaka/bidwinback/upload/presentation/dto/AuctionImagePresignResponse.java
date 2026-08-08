package com.tikitaka.bidwinback.upload.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuctionImagePresignResponse(
        UUID uploadId,
        String presignedUrl,
        Map<String, List<String>> signedHeaders,
        Instant expiresAt
) {
}
