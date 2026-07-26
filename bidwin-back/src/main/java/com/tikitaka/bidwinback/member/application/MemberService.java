package com.tikitaka.bidwinback.member.application;

import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public Member create(
            String email,
            String encodedPassword,
            String name,
            String phoneNumber,
            String nickname
    ) {
        Member member = Member.builder()
                .email(email)
                .password(encodedPassword)
                .name(name)
                .phoneNumber(phoneNumber)
                .nickname(nickname)
                .build();

        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }
}
