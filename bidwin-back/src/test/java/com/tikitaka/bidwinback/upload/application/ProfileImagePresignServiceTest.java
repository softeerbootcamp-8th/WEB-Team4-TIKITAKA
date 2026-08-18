package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.global.storage.PresignedUpload;
import com.tikitaka.bidwinback.upload.domain.enums.ProfileImageFileType;
import com.tikitaka.bidwinback.upload.domain.repository.PendingProfileImageStore;
import com.tikitaka.bidwinback.upload.presentation.dto.request.ProfileImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.response.ProfileImagePresignResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileImagePresignServiceTest {

    @Test
    void 프로필_이미지_Presigned_URL을_발급하고_발급_기록을_저장한다() {
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        ProfileImageObjectKeyGenerator keyGenerator =
                mock(ProfileImageObjectKeyGenerator.class);
        PendingProfileImageStore store = mock(PendingProfileImageStore.class);
        ProfileImagePresignService service = new ProfileImagePresignService(
                objectStorage,
                keyGenerator,
                store
        );
        ProfileImagePresignRequest request = new ProfileImagePresignRequest(
                "profile.jpeg",
                "image/jpeg",
                123_456L
        );
        String objectKey = "profile-images/1/image.jpg";
        Instant expiresAt = Instant.parse("2026-08-06T12:05:00Z");
        Map<String, List<String>> headers = Map.of(
                "Content-Type",
                List.of("image/jpeg")
        );
        when(keyGenerator.generate(1L, ProfileImageFileType.JPEG))
                .thenReturn(objectKey);
        when(objectStorage.presignPut(objectKey, "image/jpeg", 123_456L))
                .thenReturn(new PresignedUpload(
                        "https://example.com/upload",
                        headers,
                        expiresAt
                ));

        ProfileImagePresignResponse result = service.issue(1L, request);

        assertThat(result).isEqualTo(new ProfileImagePresignResponse(
                "https://example.com/upload",
                objectKey,
                headers,
                expiresAt
        ));
        verify(store).save(1L, objectKey);
    }
}
