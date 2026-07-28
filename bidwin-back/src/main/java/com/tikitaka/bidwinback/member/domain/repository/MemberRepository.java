package com.tikitaka.bidwinback.member.domain.repository;

import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByIdAndStatus(Long id, MemberStatus status);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}
