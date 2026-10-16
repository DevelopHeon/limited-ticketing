package com.example.limitedticketing.purchase.infrastructure.adapter.out.module;

import com.example.limitedticketing.member.application.port.in.MemberPurchaseEligibilityPort;
import com.example.limitedticketing.member.application.port.in.dto.EnsureMemberPurchasableInput;
import com.example.limitedticketing.purchase.application.port.out.MemberEligibilityPort;
import org.springframework.stereotype.Component;

@Component
public class PurchaseMemberModuleAdapter implements MemberEligibilityPort {

    private final MemberPurchaseEligibilityPort memberPurchaseEligibilityPort;

    public PurchaseMemberModuleAdapter(MemberPurchaseEligibilityPort memberPurchaseEligibilityPort) {
        this.memberPurchaseEligibilityPort = memberPurchaseEligibilityPort;
    }

    @Override
    public void ensurePurchasable(Long memberId) {
        memberPurchaseEligibilityPort.ensurePurchasable(new EnsureMemberPurchasableInput(memberId));
    }
}
