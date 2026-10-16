package com.example.limitedticketing.purchase.application.port.out;

public interface MemberEligibilityPort {

    void ensurePurchasable(Long memberId);
}
