package com.example.limitedticketing.member.application.port.in;

import com.example.limitedticketing.member.application.port.in.dto.EnsureMemberPurchasableInput;

public interface MemberPurchaseEligibilityPort {

    void ensurePurchasable(EnsureMemberPurchasableInput input);
}
