package com.example.limitedticketing.member.infrastructure.persistence.entity;

import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.model.MemberStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "members",
        uniqueConstraints = @UniqueConstraint(name = "uk_member_external_id", columnNames = "external_id")
)
public class MemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false, length = 100)
    private String externalId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    protected MemberJpaEntity() {
    }

    private MemberJpaEntity(Member member) {
        this.id = member.getId();
        this.externalId = member.getExternalId();
        this.name = member.getName();
        this.status = member.getStatus();
    }

    public static MemberJpaEntity from(Member member) {
        return new MemberJpaEntity(member);
    }

    public Member toDomain() {
        return Member.reconstitute(id, externalId, name, status);
    }
}
