package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.module;

import com.example.limitedticketing.member.application.port.in.MemberPurchaseEligibilityFacade;
import com.example.limitedticketing.member.application.port.in.dto.EnsureMemberPurchasableInput;
import com.example.limitedticketing.purchasewaiting.application.port.out.MemberEligibilityPort;
import org.springframework.stereotype.Component;

@Component
public class MemberModuleAdapter implements MemberEligibilityPort {

    private final MemberPurchaseEligibilityFacade memberPurchaseEligibilityFacade;

    public MemberModuleAdapter(
        MemberPurchaseEligibilityFacade memberPurchaseEligibilityFacade
    ) {
        this.memberPurchaseEligibilityFacade = memberPurchaseEligibilityFacade;
    }

    @Override
    public void ensurePurchasable(Long memberId) {
        memberPurchaseEligibilityFacade.ensurePurchasable(new EnsureMemberPurchasableInput(memberId));
    }
}
