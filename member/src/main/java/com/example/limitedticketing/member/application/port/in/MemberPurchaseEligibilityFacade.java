package com.example.limitedticketing.member.application.port.in;

import com.example.limitedticketing.member.application.port.in.dto.EnsureMemberPurchasableInput;
import com.example.limitedticketing.member.application.query.dto.EnsureMemberPurchasableQuery;
import com.example.limitedticketing.member.application.query.service.MemberQueryService;
import org.springframework.stereotype.Component;

@Component
public class MemberPurchaseEligibilityFacade {

    private final MemberQueryService memberQueryService;

    public MemberPurchaseEligibilityFacade(MemberQueryService memberQueryService) {
        this.memberQueryService = memberQueryService;
    }

    public void ensurePurchasable(EnsureMemberPurchasableInput input) {
        memberQueryService.ensurePurchasable(new EnsureMemberPurchasableQuery(input.memberId()));
    }
}
