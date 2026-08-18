package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.upload.domain.enums.AuctionImageFileType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuctionImageObjectKeyGenerator {
    private static final String TEMP_PREFIX = "temp";
    private static final String PERMANENT_PREFIX = "auction-images";

    public UUID generateUploadId() {
        return UUID.randomUUID();
    }

    public String generateTemporary(UUID uploadId) {
        return "%s/%s".formatted(TEMP_PREFIX, uploadId);
    }

    public String generatePermanent(
            long auctionId,
            UUID uploadId,
            AuctionImageFileType fileType
    ) {
        return "%s/%d/%s.%s".formatted(
                PERMANENT_PREFIX,
                auctionId,
                uploadId,
                fileType.getObjectExtension()
        );
    }
}
