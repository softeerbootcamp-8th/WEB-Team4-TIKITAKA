package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.dto.SignUpRequest;
import com.tikitaka.bidwinback.dto.SignUpResponse;
import com.tikitaka.bidwinback.member.application.MemberService;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordHasher passwordHasher;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(new MemberService(memberRepository), passwordHasher);
    }

    @Test
    void 회원가입_시_비밀번호를_해싱하고_회원을_저장한다() {
        SignUpRequest request = new SignUpRequest(
                "member@example.com",
                "password!",
                "홍길동",
                "01012345678",
                "티키타카"
        );
        when(passwordHasher.hash(request.password())).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SignUpResponse response = authService.signup(request);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member savedMember = captor.getValue();
        assertAll(
                () -> assertEquals("encoded-password", savedMember.getPassword()),
                () -> assertEquals(request.email(), savedMember.getEmail()),
                () -> assertEquals(request.name(), savedMember.getName()),
                () -> assertEquals(request.phoneNumber(), savedMember.getPhoneNumber()),
                () -> assertEquals(request.nickname(), savedMember.getNickname()),
                () -> assertFalse(savedMember.getProfileObjectKey().isBlank()),
                () -> assertEquals(MemberStatus.PENDING, savedMember.getStatus()),
                () -> assertEquals(request.email(), response.email()),
                () -> assertEquals(request.nickname(), response.nickname())
        );
    }
}
