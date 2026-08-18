package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.config.PendingProfileImageProperties;
import com.tikitaka.bidwinback.global.storage.ObjectDeletionResult;
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.upload.domain.entity.PendingProfileImage;
import com.tikitaka.bidwinback.upload.domain.repository.PendingProfileImageStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PendingProfileImageCleanupServiceTest {

    private static final Duration RETENTION = Duration.ofHours(24);
    private static final int BATCH_SIZE = 100;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-06T12:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void 만료된_프로필_이미지를_잠그고_스토리지와_DB에서_삭제한다() {
        PendingProfileImageStore store = mock(PendingProfileImageStore.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        PendingProfileImageCleanupService service = service(store, objectStorage);
        List<PendingProfileImage> pendingImages = List.of(
                PendingProfileImage.issue(1L, "profile-images/1/first.jpg"),
                PendingProfileImage.issue(1L, "profile-images/1/second.jpg")
        );
        List<String> keys = pendingImages.stream()
                .map(PendingProfileImage::getObjectKey)
                .toList();
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                CLOCK.instant().minus(RETENTION),
                ZoneId.systemDefault()
        );
        when(store.findExpiredBeforeForUpdate(cutoff, BATCH_SIZE))
                .thenReturn(pendingImages);
        when(objectStorage.deleteAll(keys))
                .thenReturn(new ObjectDeletionResult(keys, List.of()));

        int result = service.cleanup();

        assertThat(result).isEqualTo(2);
        verify(store).deleteByObjectKeyIn(keys);
    }

    @Test
    void 삭제에_실패한_이미지는_DB에_남겨_다음_실행에서_재시도한다() {
        PendingProfileImageStore store = mock(PendingProfileImageStore.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        PendingProfileImageCleanupService service = service(store, objectStorage);
        String first = "profile-images/1/first.jpg";
        String second = "profile-images/1/second.jpg";
        when(store.findExpiredBeforeForUpdate(any(LocalDateTime.class), eq(BATCH_SIZE)))
                .thenReturn(List.of(
                        PendingProfileImage.issue(1L, first),
                        PendingProfileImage.issue(1L, second)
                ));
        when(objectStorage.deleteAll(List.of(first, second))).thenReturn(
                new ObjectDeletionResult(
                        List.of(first),
                        List.of(new ObjectDeletionResult.Failure(
                                second,
                                "InternalError"
                        ))
                )
        );

        int result = service.cleanup();

        assertThat(result).isOne();
        verify(store).deleteByObjectKeyIn(List.of(first));
    }

    @Test
    void 스토리지_요청_자체가_실패하면_DB에서_삭제하지_않는다() {
        PendingProfileImageStore store = mock(PendingProfileImageStore.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        PendingProfileImageCleanupService service = service(store, objectStorage);
        String objectKey = "profile-images/1/image.jpg";
        when(store.findExpiredBeforeForUpdate(any(LocalDateTime.class), eq(BATCH_SIZE)))
                .thenReturn(List.of(PendingProfileImage.issue(1L, objectKey)));
        when(objectStorage.deleteAll(List.of(objectKey)))
                .thenThrow(new IllegalStateException("S3 unavailable"));

        assertThatThrownBy(service::cleanup)
                .isInstanceOf(IllegalStateException.class);

        verify(store, never()).deleteByObjectKeyIn(anyList());
    }

    @Test
    void 만료된_이미지가_없으면_스토리지를_호출하지_않는다() {
        PendingProfileImageStore store = mock(PendingProfileImageStore.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        PendingProfileImageCleanupService service = service(store, objectStorage);
        when(store.findExpiredBeforeForUpdate(any(LocalDateTime.class), eq(BATCH_SIZE)))
                .thenReturn(List.of());

        assertThat(service.cleanup()).isZero();

        verifyNoInteractions(objectStorage);
        verify(store, never()).deleteByObjectKeyIn(anyList());
    }

    private PendingProfileImageCleanupService service(
            PendingProfileImageStore store,
            ObjectStorage objectStorage
    ) {
        return new PendingProfileImageCleanupService(
                store,
                objectStorage,
                new PendingProfileImageProperties(RETENTION, BATCH_SIZE),
                CLOCK
        );
    }
}
