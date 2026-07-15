package com.example.limitedticketing.member.infrastructure.persistence.repository;

import com.example.limitedticketing.member.infrastructure.persistence.entity.MemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMemberRepository extends JpaRepository<MemberJpaEntity, Long> {
}
