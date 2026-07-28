package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.upload.domain.AuctionImageFileType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuctionImageObjectKeyGenerator {
    private static final String PREFIX = "auction-images";

    public String generate(AuctionImageFileType fileType) {
        return "%s/%s.%s".formatted(
                PREFIX,
                UUID.randomUUID(),
                fileType.getObjectExtension()
        );
    }
}
