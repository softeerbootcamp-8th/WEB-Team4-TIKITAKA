package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.global.storage.PresignedUpload;
import com.tikitaka.bidwinback.upload.domain.enums.ProfileImageFileType;
import com.tikitaka.bidwinback.upload.domain.repository.PendingProfileImageStore;
import com.tikitaka.bidwinback.upload.presentation.dto.request.ProfileImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.response.ProfileImagePresignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileImagePresignService {

    private final ObjectStorage objectStorage;
    private final ProfileImageObjectKeyGenerator objectKeyGenerator;
    private final PendingProfileImageStore pendingProfileImageStore;

    @Transactional
    public ProfileImagePresignResponse issue(
            long memberId,
            ProfileImagePresignRequest request
    ) {
        ProfileImageFileType fileType = ProfileImageFileType.from(
                request.fileName(),
                request.contentType()
        );
        String objectKey = objectKeyGenerator.generate(memberId, fileType);
        PresignedUpload presignedUpload = objectStorage.presignPut(
                objectKey,
                fileType.getContentType(),
                request.size()
        );

        pendingProfileImageStore.save(memberId, objectKey);

        return new ProfileImagePresignResponse(
                presignedUpload.url(),
                objectKey,
                presignedUpload.signedHeaders(),
                presignedUpload.expiresAt()
        );
    }
}
