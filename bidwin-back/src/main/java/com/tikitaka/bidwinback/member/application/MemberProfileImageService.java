package com.tikitaka.bidwinback.member.application;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import com.tikitaka.bidwinback.member.presentation.dto.response.ProfileImageUpdateResponse;
import com.tikitaka.bidwinback.upload.application.ProfileImageObjectKeyGenerator;
import com.tikitaka.bidwinback.upload.domain.PendingProfileImageStore;
import com.tikitaka.bidwinback.upload.domain.UploadException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberProfileImageService {

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
        member.changeProfileImage(objectKey);
        pendingProfileImageStore.deleteByObjectKeyIn(List.of(objectKey));

        return resolveResponse(objectKey);
    }

    @Transactional
    public ProfileImageUpdateResponse reset(long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(ErrorCode.MEMBER_NOT_FOUND));
        member.resetProfileImage();

        return resolveResponse(member.getProfileObjectKey());
    }

    private void validatePendingImage(long memberId, String objectKey) {
        if (pendingProfileImageStore
                .findByMemberIdAndObjectKey(memberId, objectKey)
                .isEmpty()
                || !objectStorage.exists(objectKey)) {
            throw new UploadException(ErrorCode.INVALID_IMAGE_REFERENCE);
        }
    }

    private ProfileImageUpdateResponse resolveResponse(String objectKey) {
        return new ProfileImageUpdateResponse(imageUrlResolver.resolve(objectKey));
    }
}
