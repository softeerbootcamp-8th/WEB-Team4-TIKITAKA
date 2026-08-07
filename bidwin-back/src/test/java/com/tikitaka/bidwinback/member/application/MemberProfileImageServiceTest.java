package com.tikitaka.bidwinback.member.application;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import com.tikitaka.bidwinback.member.presentation.dto.response.ProfileImageUpdateResponse;
import com.tikitaka.bidwinback.upload.application.ProfileImageObjectKeyGenerator;
import com.tikitaka.bidwinback.upload.domain.PendingProfileImageStore;
import com.tikitaka.bidwinback.upload.domain.UploadException;
import com.tikitaka.bidwinback.upload.domain.entity.PendingProfileImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberProfileImageServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final String NEW_KEY = "profile-images/1/new.jpg";
    private static final String OLD_KEY = "profile-images/1/old.jpg";
    private static final String DEFAULT_KEY = "profiles/default-profile.png";

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PendingProfileImageStore pendingProfileImageStore;
    @Mock
    private ObjectStorage objectStorage;
    @Mock
    private ImageUrlResolver imageUrlResolver;

    private MemberProfileImageService service;

    @BeforeEach
    void setUp() {
        service = new MemberProfileImageService(
                memberRepository,
                pendingProfileImageStore,
                new ProfileImageObjectKeyGenerator(),
                objectStorage,
                imageUrlResolver
        );
    }

    @Test
    void 업로드된_이미지로_프로필을_변경하고_발급_기록을_소비한다() {
        Member member = mock(Member.class);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getProfileObjectKey()).thenReturn(OLD_KEY);
        when(pendingProfileImageStore.findByMemberIdAndObjectKeyForUpdate(MEMBER_ID, NEW_KEY))
                .thenReturn(Optional.of(PendingProfileImage.issue(MEMBER_ID, NEW_KEY)));
        when(objectStorage.exists(NEW_KEY)).thenReturn(true);
        when(imageUrlResolver.resolve(NEW_KEY)).thenReturn("https://cdn.example.com/new.jpg");

        ProfileImageUpdateResponse result = service.change(MEMBER_ID, NEW_KEY);

        assertThat(result.profileImageUrl()).isEqualTo("https://cdn.example.com/new.jpg");
        verify(member).changeProfileImage(NEW_KEY);
        verify(pendingProfileImageStore).deleteByObjectKeyIn(List.of(NEW_KEY));
        verify(pendingProfileImageStore).save(MEMBER_ID, OLD_KEY);
    }

    @Test
    void 기본_이미지는_정리_대상으로_저장하지_않는다() {
        Member member = mock(Member.class);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getProfileObjectKey()).thenReturn(DEFAULT_KEY);
        when(pendingProfileImageStore.findByMemberIdAndObjectKeyForUpdate(MEMBER_ID, NEW_KEY))
                .thenReturn(Optional.of(PendingProfileImage.issue(MEMBER_ID, NEW_KEY)));
        when(objectStorage.exists(NEW_KEY)).thenReturn(true);

        service.change(MEMBER_ID, NEW_KEY);

        verify(pendingProfileImageStore, never()).save(MEMBER_ID, DEFAULT_KEY);
    }

    @Test
    void 이미_적용된_이미지로_재요청하면_검증_없이_성공한다() {
        Member member = mock(Member.class);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getProfileObjectKey()).thenReturn(NEW_KEY);
        when(imageUrlResolver.resolve(NEW_KEY)).thenReturn("https://cdn.example.com/new.jpg");

        ProfileImageUpdateResponse result = service.change(MEMBER_ID, NEW_KEY);

        assertThat(result.profileImageUrl()).isEqualTo("https://cdn.example.com/new.jpg");
        verifyNoInteractions(pendingProfileImageStore, objectStorage);
        verify(member, never()).changeProfileImage(NEW_KEY);
    }

    @Test
    void 다른_회원_경로의_이미지는_거절한다() {
        assertThatExceptionOfType(UploadException.class)
                .isThrownBy(() -> service.change(MEMBER_ID, "profile-images/2/image.jpg"))
                .extracting(UploadException::getErrorCode)
                .isEqualTo(ErrorCode.INVALID_IMAGE_REFERENCE);
        verifyNoInteractions(memberRepository, pendingProfileImageStore, objectStorage);
    }

    @Test
    void 발급_기록이_없는_이미지는_거절한다() {
        Member member = mock(Member.class);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(pendingProfileImageStore.findByMemberIdAndObjectKeyForUpdate(MEMBER_ID, NEW_KEY))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(UploadException.class)
                .isThrownBy(() -> service.change(MEMBER_ID, NEW_KEY))
                .extracting(UploadException::getErrorCode)
                .isEqualTo(ErrorCode.INVALID_IMAGE_REFERENCE);
        verifyNoInteractions(objectStorage);
    }

    @Test
    void S3에_업로드되지_않은_이미지는_거절한다() {
        Member member = mock(Member.class);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(pendingProfileImageStore.findByMemberIdAndObjectKeyForUpdate(MEMBER_ID, NEW_KEY))
                .thenReturn(Optional.of(PendingProfileImage.issue(MEMBER_ID, NEW_KEY)));
        when(objectStorage.exists(NEW_KEY)).thenReturn(false);

        assertThatExceptionOfType(UploadException.class)
                .isThrownBy(() -> service.change(MEMBER_ID, NEW_KEY))
                .extracting(UploadException::getErrorCode)
                .isEqualTo(ErrorCode.INVALID_IMAGE_REFERENCE);
        verify(member, never()).changeProfileImage(NEW_KEY);
    }

    @Test
    void 프로필_이미지를_기본값으로_초기화한다() {
        Member member = mock(Member.class);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getProfileObjectKey()).thenReturn(DEFAULT_KEY);
        when(imageUrlResolver.resolve(DEFAULT_KEY)).thenReturn("https://cdn.example.com/default.png");

        ProfileImageUpdateResponse result = service.reset(MEMBER_ID);

        assertThat(result.profileImageUrl()).isEqualTo("https://cdn.example.com/default.png");
        verify(member).resetProfileImage();
        verifyNoInteractions(pendingProfileImageStore, objectStorage);
    }

    @Test
    void 초기화하면_이전_사용자_이미지를_정리_대상으로_저장한다() {
        Member member = mock(Member.class);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getProfileObjectKey())
                .thenReturn(OLD_KEY, DEFAULT_KEY);
        when(imageUrlResolver.resolve(DEFAULT_KEY))
                .thenReturn("https://cdn.example.com/default.png");

        service.reset(MEMBER_ID);

        verify(member).resetProfileImage();
        verify(pendingProfileImageStore).save(MEMBER_ID, OLD_KEY);
    }
}
