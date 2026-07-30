package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.config.PendingAuctionImageProperties;
import com.tikitaka.bidwinback.global.config.S3Properties;
import com.tikitaka.bidwinback.upload.domain.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingAuctionImageCleanupService {

    private final PendingAuctionImageStore pendingAuctionImageStore;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final PendingAuctionImageProperties cleanupProperties;
    private final Clock clock;

    public int cleanup() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                clock.instant().minus(cleanupProperties.retention()),
                ZoneId.systemDefault()
        );
        List<PendingAuctionImage> expiredImages =
                pendingAuctionImageStore.findExpiredBefore(
                        cutoff,
                        cleanupProperties.cleanupBatchSize()
                );

        if (expiredImages.isEmpty()) {
            return 0;
        }

        DeleteObjectsResponse response = s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                        .bucket(s3Properties.bucket())
                        .delete(Delete.builder()
                                .objects(expiredImages.stream()
                                        .map(PendingAuctionImage::getObjectKey)
                                        .map(
                                                key -> ObjectIdentifier.builder()
                                                        .key(key)
                                                        .build()
                                        )
                                        .toList())
                                .build())
                        .build()
        );

        // 삭제 실패시
        Set<String> failedKeys = new HashSet<>();
        response.errors().forEach(error -> {
            failedKeys.add(error.key());
            log.warn(
                    "만료 경매 이미지 삭제 실패: objectKey={}, code={}",
                    error.key(),
                    error.code()
            );
        });

        // 만료된 이미지들 중 s3에서 삭제된 이미지 key만 추출
        List<String> deletedKeys = expiredImages.stream()
                .map(PendingAuctionImage::getObjectKey)
                .filter(key -> !failedKeys.contains(key))
                .toList();

        if (!deletedKeys.isEmpty()) {
            pendingAuctionImageStore.deleteByObjectKeyIn(deletedKeys);
        }

        return deletedKeys.size();
    }
}
