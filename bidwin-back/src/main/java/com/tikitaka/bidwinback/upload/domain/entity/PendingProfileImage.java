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

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "pending_profile_image",
        indexes = @Index(
                name = "idx_pending_profile_image_created_at",
                columnList = "created_at"
        )
)
@NoArgsConstructor(access = PROTECTED)
public class PendingProfileImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "object_key", nullable = false, length = 100, unique = true)
    private String objectKey;

    private PendingProfileImage(Long memberId, String objectKey) {
        this.memberId = memberId;
        this.objectKey = objectKey;
    }

    public static PendingProfileImage issue(Long memberId, String objectKey) {
        return new PendingProfileImage(memberId, objectKey);
    }
}
