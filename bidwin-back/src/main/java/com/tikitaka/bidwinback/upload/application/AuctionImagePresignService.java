package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.config.S3Properties;
import com.tikitaka.bidwinback.upload.domain.AuctionImageFileType;
import com.tikitaka.bidwinback.upload.domain.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuctionImagePresignService {
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
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

        // 실제 업로드는 수행하지 않고, 서명할 S3 PUT 요청 조건만 구성한다.
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(objectKey)
                .contentType(fileType.getContentType())
                .contentLength(request.size())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(s3Properties.presignDuration())
                .putObjectRequest(putObjectRequest)
                .build();

        // S3 PUT 요청에 대한 Presigned URL을 생성한다.
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        Map<String, List<String>> signedHeaders = presignedRequest.signedHeaders();

        return new AuctionImagePresignResponse(
                presignedRequest.url().toString(),
                objectKey,
                signedHeaders,
                presignedRequest.expiration()
        );
    }
}
