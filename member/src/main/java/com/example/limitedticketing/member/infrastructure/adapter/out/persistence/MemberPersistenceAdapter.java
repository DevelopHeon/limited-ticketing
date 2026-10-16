package com.example.limitedticketing.member.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.member.infrastructure.adapter.out.persistence.entity.MemberJpaEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MemberPersistenceAdapter implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;

    public MemberPersistenceAdapter(MemberJpaRepository memberJpaRepository) {
        this.memberJpaRepository = memberJpaRepository;
    }

    @Override
    public Member save(Member member) {
        return memberJpaRepository.save(MemberJpaEntity.from(member)).toDomain();
    }

    @Override
    public Optional<Member> findById(Long memberId) {
        return memberJpaRepository.findById(memberId).map(MemberJpaEntity::toDomain);
    }
}
