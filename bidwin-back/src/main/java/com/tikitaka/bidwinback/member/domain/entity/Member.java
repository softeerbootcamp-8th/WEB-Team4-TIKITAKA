package com.tikitaka.bidwinback.member.domain.entity;

import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "Member",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = Member.EMAIL_UNIQUE_CONSTRAINT,
                        columnNames = "email"
                ),
                @UniqueConstraint(
                        name = Member.NICKNAME_UNIQUE_CONSTRAINT,
                        columnNames = "nickname"
                )
        }
)
@NoArgsConstructor(access = PROTECTED)
public class Member {

    public static final String EMAIL_UNIQUE_CONSTRAINT = "uk_member_email";
    public static final String NICKNAME_UNIQUE_CONSTRAINT = "uk_member_nickname";

    private static final long INITIAL_POINT = 2_000_000L;
    private static final String DEFAULT_PROFILE_OBJECT_KEY = "profiles/default-profile.png";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 17)
    private String name;

    @Column(name = "phone_number", nullable = false, length = 11)
    private String phoneNumber;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 128)
    private String password;

    @Column(name = "total_point", nullable = false)
    private long totalPoint;

    @Column(name = "profile_object_key", nullable = false, length = 100)
    private String profileObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Column(name = "locked_point", nullable = false)
    private long lockedPoint;

    @Builder
    private Member(
            String name,
            String phoneNumber,
            String nickname,
            String email,
            String password,
            Long totalPoint,
            String profileObjectKey,
            MemberStatus status,
            Long lockedPoint
    ) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.nickname = nickname;
        this.email = email;
        this.password = password;
        this.totalPoint = totalPoint == null ? INITIAL_POINT : totalPoint;
        this.profileObjectKey = profileObjectKey == null
                ? DEFAULT_PROFILE_OBJECT_KEY
                : profileObjectKey;
        this.status = status == null ? MemberStatus.PENDING : status;
        this.lockedPoint = lockedPoint == null ? 0L : lockedPoint;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
