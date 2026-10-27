package com.example.limitedticketing.member.application.service;

import com.example.limitedticketing.member.application.usecase.EnsureMemberPurchasableUseCase;
import com.example.limitedticketing.member.application.usecase.dto.query.EnsureMemberPurchasableQuery;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

@Service
public class MemberQueryService implements EnsureMemberPurchasableUseCase {

    private final MemberRepository memberRepository;

    public MemberQueryService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public void ensurePurchasable(EnsureMemberPurchasableQuery query) {
        Member member = memberRepository.getRequiredById(query.memberId());

        member.ensurePurchasable();
    }
}
