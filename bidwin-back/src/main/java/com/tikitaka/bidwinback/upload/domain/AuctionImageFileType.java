package com.tikitaka.bidwinback.upload.domain;

import com.tikitaka.bidwinback.global.exception.ErrorCode;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

public enum AuctionImageFileType {
    JPEG("image/jpeg", Set.of("jpg", "jpeg"), "jpg"),
    PNG("image/png", Set.of("png"), "png"),
    WEBP("image/webp", Set.of("webp"), "webp");

    private final String contentType;
    private final Set<String> extensions;
    private final String objectExtension;

    AuctionImageFileType(
            String contentType,
            Set<String> extensions,
            String objectExtension
    ) {
        this.contentType = contentType;
        this.extensions = extensions;
        this.objectExtension = objectExtension;
    }

    public static AuctionImageFileType from(
            String fileName,
            String contentType
    ) {
        String extension = extractExtension(fileName);

        return Arrays.stream(values())
                .filter(type -> type.contentType.equalsIgnoreCase(contentType))
                .filter(type -> type.extensions.contains(extension))
                .findFirst()
                .orElseThrow(() ->
                        new UploadException(ErrorCode.UNSUPPORTED_IMAGE_TYPE));
    }

    private static String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    public String getContentType() {
        return contentType;
    }

    public String getObjectExtension() {
        return objectExtension;
    }
}
