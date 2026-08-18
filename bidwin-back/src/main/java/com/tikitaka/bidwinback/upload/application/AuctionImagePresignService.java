package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.global.storage.PresignedUpload;
import com.tikitaka.bidwinback.upload.domain.AuctionImageUploadReservation;
import com.tikitaka.bidwinback.upload.domain.enums.AuctionImageFileType;
import com.tikitaka.bidwinback.upload.domain.repository.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.presentation.dto.request.AuctionImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.response.AuctionImagePresignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

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
        List<AuctionImageFileType> fileTypes = requests.stream()
                .map(request -> AuctionImageFileType.from(
                        request.fileName(),
                        request.contentType()
                ))
                .toList();
        List<IssuedUpload> issuedUploads = IntStream
                .range(0, requests.size())
                .mapToObj(index -> issueOne(requests.get(index), fileTypes.get(index)))
                .toList();

        // URL을 반환하기 전에 소유자·draft·서명 조건을 저장해 경매 등록 시 검증 기준으로 사용한다.
        pendingAuctionImageStore.saveAll(
                memberId,
                draftId,
                issuedUploads.stream()
                        .map(IssuedUpload::reservation)
                        .toList()
        );

        return issuedUploads.stream()
                .map(IssuedUpload::response)
                .toList();
    }

    private IssuedUpload issueOne(
            AuctionImagePresignRequest request,
            AuctionImageFileType fileType
    ) {
        // uploadId는 S3가 아닌 백엔드가 생성하며, 임시 객체 키와 업로드 예약을 연결하는 식별자다.
        UUID uploadId = objectKeyGenerator.generateUploadId();
        String objectKey = objectKeyGenerator.generateTemporary(uploadId);

        PresignedUpload presignedUpload = objectStorage.presignPut(
                objectKey,
                fileType.getContentType(),
                request.size(),
                request.checksumSha256()
        );

        AuctionImageUploadReservation reservation = new AuctionImageUploadReservation(
                uploadId,
                objectKey,
                fileType.getContentType(),
                request.size(),
                request.checksumSha256()
        );
        AuctionImagePresignResponse response = new AuctionImagePresignResponse(
                uploadId,
                presignedUpload.url(),
                presignedUpload.signedHeaders(),
                presignedUpload.expiresAt()
        );
        return new IssuedUpload(reservation, response);
    }

    private record IssuedUpload(
            AuctionImageUploadReservation reservation,
            AuctionImagePresignResponse response
    ) {
    }
}
