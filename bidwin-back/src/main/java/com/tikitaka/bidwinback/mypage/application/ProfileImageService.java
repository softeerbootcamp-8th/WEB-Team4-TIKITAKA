package com.tikitaka.bidwinback.mypage.application;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.ProfileImageUpdateResponse;
import com.tikitaka.bidwinback.upload.application.ProfileImageObjectKeyGenerator;
import com.tikitaka.bidwinback.upload.domain.exception.UploadException;
import com.tikitaka.bidwinback.upload.domain.repository.PendingProfileImageStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private final MemberRepository memberRepository;
    private final PendingProfileImageStore pendingProfileImageStore;
    private final ProfileImageObjectKeyGenerator objectKeyGenerator;
    private final ObjectStorage objectStorage;
    private final ImageUrlResolver imageUrlResolver;

    @Transactional
    public ProfileImageUpdateResponse change(long memberId, String objectKey) {
        if (!objectKeyGenerator.belongsTo(memberId, objectKey)) {
            throw new UploadException(ErrorCode.INVALID_IMAGE_REFERENCE);
        }

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(ErrorCode.MEMBER_NOT_FOUND));
        if (objectKey.equals(member.getProfileObjectKey())) {
            return resolveResponse(objectKey);
        }

        validatePendingImage(memberId, objectKey);
        String previousObjectKey = member.getProfileObjectKey();
        member.changeProfileImage(objectKey);
        pendingProfileImageStore.deleteByObjectKeyIn(List.of(objectKey));
        enqueueUnusedImage(memberId, previousObjectKey, objectKey);

        return resolveResponse(objectKey);
    }

    @Transactional
    public ProfileImageUpdateResponse reset(long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(ErrorCode.MEMBER_NOT_FOUND));
        String previousObjectKey = member.getProfileObjectKey();
        member.resetProfileImage();
        enqueueUnusedImage(
                memberId,
                previousObjectKey,
                member.getProfileObjectKey()
        );

        return resolveResponse(member.getProfileObjectKey());
    }

    private void validatePendingImage(long memberId, String objectKey) {
        if (pendingProfileImageStore
                .findByMemberIdAndObjectKeyForUpdate(memberId, objectKey)
                .isEmpty()
                || !objectStorage.exists(objectKey)) {
            throw new UploadException(ErrorCode.INVALID_IMAGE_REFERENCE);
        }
    }

    private ProfileImageUpdateResponse resolveResponse(String objectKey) {
        return new ProfileImageUpdateResponse(imageUrlResolver.resolve(objectKey));
    }

    private void enqueueUnusedImage(
            long memberId,
            String previousObjectKey,
            String currentObjectKey
    ) {
        // 이전 사용자 이미지를 보관 기간 이후 정리할 수 있도록 다시 대기열에 넣는다.
        if (!previousObjectKey.equals(currentObjectKey)
                && objectKeyGenerator.belongsTo(memberId, previousObjectKey)) {
            pendingProfileImageStore.save(memberId, previousObjectKey);
        }
    }
}
