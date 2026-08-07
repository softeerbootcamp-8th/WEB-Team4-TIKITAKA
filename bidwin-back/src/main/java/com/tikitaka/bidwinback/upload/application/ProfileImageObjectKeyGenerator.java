package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.upload.domain.ProfileImageFileType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProfileImageObjectKeyGenerator {

    private static final String PREFIX = "profile-images";

    public String generate(long memberId, ProfileImageFileType fileType) {
        return "%s/%d/%s.%s".formatted(
                PREFIX,
                memberId,
                UUID.randomUUID(),
                fileType.getObjectExtension()
        );
    }

    public boolean belongsTo(long memberId, String objectKey) {
        return objectKey != null
                && objectKey.startsWith("%s/%d/".formatted(PREFIX, memberId));
    }
}
