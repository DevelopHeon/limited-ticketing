package com.example.limitedticketing.member.application.query.service;

import com.example.limitedticketing.member.application.query.dto.EnsureMemberPurchasableQuery;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.exception.MemberException;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

@Service
public class MemberQueryService {

    private final MemberRepository memberRepository;

    public MemberQueryService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public void ensurePurchasable(EnsureMemberPurchasableQuery query) {
        Member member = memberRepository.findById(query.memberId())
            .orElseThrow(MemberException::notFound);

        member.ensurePurchasable();
    }
}
