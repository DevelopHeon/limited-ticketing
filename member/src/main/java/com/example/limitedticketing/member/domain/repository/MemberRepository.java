package com.example.limitedticketing.member.domain.repository;

import com.example.limitedticketing.member.domain.exception.MemberException;
import com.example.limitedticketing.member.domain.model.Member;
import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long memberId);

    default Member getRequiredById(Long memberId) {
        return findById(memberId).orElseThrow(MemberException::notFound);
    }
}
