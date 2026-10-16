package com.example.limitedticketing.purchasewaiting.application.port.out;

public interface MemberEligibilityPort {

    void ensurePurchasable(Long memberId);
}
