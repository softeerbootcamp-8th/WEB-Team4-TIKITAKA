package com.tikitaka.bidwinback.upload.presentation.dto;

import java.time.LocalDateTime;

public record AuctionImagePresignResponse(
        String uploadUrl,
        String objectKey,
        LocalDateTime expiresAt
) {
}
