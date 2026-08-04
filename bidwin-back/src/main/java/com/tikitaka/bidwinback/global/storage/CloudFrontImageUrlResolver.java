package com.tikitaka.bidwinback.global.storage;

import com.tikitaka.bidwinback.global.config.CloudFrontProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class CloudFrontImageUrlResolver implements ImageUrlResolver {

    private final URI baseUri;

    public CloudFrontImageUrlResolver(CloudFrontProperties properties) {
        String domain = properties.domain().trim();
        if (!domain.startsWith("https://") && !domain.startsWith("http://")) {
            domain = "https://" + domain;
        }
        if (!domain.endsWith("/")) {
            domain += "/";
        }
        this.baseUri = URI.create(domain);
    }

    @Override
    public String resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }

        String normalizedObjectKey = objectKey;
        while (normalizedObjectKey.startsWith("/")) {
            normalizedObjectKey = normalizedObjectKey.substring(1);
        }

        // CloudFront 기본 도메인을 벗어나지 않도록 상대 경로 형태의 objectKey만 허용한다.
        URI objectKeyUri = URI.create(normalizedObjectKey);
        if (objectKeyUri.isAbsolute()
                || objectKeyUri.getRawAuthority() != null
                || normalizedObjectKey.contains("../")) {
            throw new IllegalArgumentException("objectKey must be a relative path");
        }
        return baseUri.resolve(objectKeyUri).toString();
    }
}
