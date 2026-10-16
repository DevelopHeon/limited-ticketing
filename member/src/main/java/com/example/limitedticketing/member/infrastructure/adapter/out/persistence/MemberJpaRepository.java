package com.example.limitedticketing.member.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.member.infrastructure.adapter.out.persistence.entity.MemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {
}
