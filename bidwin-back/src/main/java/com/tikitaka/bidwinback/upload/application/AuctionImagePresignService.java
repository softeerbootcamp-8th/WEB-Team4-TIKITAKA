package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.global.storage.PresignedUpload;
import com.tikitaka.bidwinback.upload.domain.AuctionImageFileType;
import com.tikitaka.bidwinback.upload.domain.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuctionImagePresignService {
    private final ObjectStorage objectStorage;
    private final AuctionImageObjectKeyGenerator objectKeyGenerator;
    private final PendingAuctionImageStore pendingAuctionImageStore;

    @Transactional
    public List<AuctionImagePresignResponse> issue(
            long memberId,
            UUID draftId,
            List<AuctionImagePresignRequest> requests
    ) {
        List<AuctionImagePresignResponse> responses = requests
                .stream()
                .map(this::issueOne)
                .toList();

        pendingAuctionImageStore.saveAll(
                memberId,
                draftId,
                responses.stream()
                        .map(AuctionImagePresignResponse::objectKey)
                        .toList()
        );

        return responses;
    }

    private AuctionImagePresignResponse issueOne(AuctionImagePresignRequest request) {

        // 파일 확장자와 MIME 타입이 허용된 조합인지 검증한다.
        AuctionImageFileType fileType = AuctionImageFileType.from(request.fileName(), request.contentType());

        String objectKey = objectKeyGenerator.generate(fileType);

        PresignedUpload presignedUpload = objectStorage.presignPut(
                objectKey,
                fileType.getContentType(),
                request.size()
        );

        return new AuctionImagePresignResponse(
                presignedUpload.url(),
                objectKey,
                presignedUpload.signedHeaders(),
                presignedUpload.expiresAt()
        );
    }
}
