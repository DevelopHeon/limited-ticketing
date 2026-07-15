package com.example.limitedticketing.member.infrastructure.persistence;

import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.member.infrastructure.persistence.entity.MemberJpaEntity;
import com.example.limitedticketing.member.infrastructure.persistence.repository.JpaMemberRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepositoryAdapter implements MemberRepository {

    private final JpaMemberRepository jpaMemberRepository;

    public MemberRepositoryAdapter(JpaMemberRepository jpaMemberRepository) {
        this.jpaMemberRepository = jpaMemberRepository;
    }

    @Override
    public Member save(Member member) {
        return jpaMemberRepository.save(MemberJpaEntity.from(member)).toDomain();
    }

    @Override
    public Optional<Member> findById(Long memberId) {
        return jpaMemberRepository.findById(memberId).map(MemberJpaEntity::toDomain);
    }
}
