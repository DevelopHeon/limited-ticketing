package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.module;

import com.example.limitedticketing.member.application.port.in.MemberPurchaseEligibilityPort;
import com.example.limitedticketing.member.application.port.in.dto.EnsureMemberPurchasableInput;
import com.example.limitedticketing.purchasewaiting.application.port.out.MemberEligibilityPort;
import org.springframework.stereotype.Component;

@Component
public class MemberModuleAdapter implements MemberEligibilityPort {

    private final MemberPurchaseEligibilityPort memberPurchaseEligibilityPort;

    public MemberModuleAdapter(
        MemberPurchaseEligibilityPort memberPurchaseEligibilityPort
    ) {
        this.memberPurchaseEligibilityPort = memberPurchaseEligibilityPort;
    }

    @Override
    public void ensurePurchasable(Long memberId) {
        memberPurchaseEligibilityPort.ensurePurchasable(new EnsureMemberPurchasableInput(memberId));
    }
}
