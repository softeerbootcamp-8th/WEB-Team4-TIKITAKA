package com.tikitaka.bidwinback.upload.domain.entity;

import com.tikitaka.bidwinback.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "pending_auction_image",
        indexes = {
                @Index(
                        name = "idx_pending_auction_image_created_at",
                        columnList = "created_at"
                )
        }
)
@NoArgsConstructor(access = PROTECTED)
public class PendingAuctionImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    // 마이그레이션 이전 대기 행은 cleanup 대상으로 남겨두기 위해 DB에서만 null을 허용한다.
    @Column(name = "upload_id", unique = true)
    private UUID uploadId;

    @Column(name = "object_key", nullable = false, length = 100, unique = true)
    private String objectKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "content_length")
    private Long contentLength;

    @Column(name = "checksum_sha256", length = 44)
    private String checksumSha256;

    private PendingAuctionImage(
            Long memberId,
            UUID draftId,
            UUID uploadId,
            String objectKey,
            String contentType,
            long contentLength,
            String checksumSha256
    ) {
        this.memberId = memberId;
        this.draftId = draftId;
        this.uploadId = uploadId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.checksumSha256 = checksumSha256;
    }

    public static PendingAuctionImage issue(
            Long memberId,
            UUID draftId,
            UUID uploadId,
            String objectKey,
            String contentType,
            long contentLength,
            String checksumSha256
    ) {
        return new PendingAuctionImage(
                memberId,
                draftId,
                uploadId,
                objectKey,
                contentType,
                contentLength,
                checksumSha256
        );
    }
}
