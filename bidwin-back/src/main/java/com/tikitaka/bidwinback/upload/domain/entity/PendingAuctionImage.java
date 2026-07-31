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

    @Column(name = "object_key", nullable = false, length = 100, unique = true)
    private String objectKey;

    private PendingAuctionImage(Long memberId, UUID draftId, String objectKey) {
        this.memberId = memberId;
        this.draftId = draftId;
        this.objectKey = objectKey;
    }

    public static PendingAuctionImage issue(Long memberId, UUID draftId, String objectKey) {
        return new PendingAuctionImage(memberId, draftId, objectKey);
    }
}
