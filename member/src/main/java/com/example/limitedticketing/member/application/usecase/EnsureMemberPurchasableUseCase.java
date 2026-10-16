package com.example.limitedticketing.member.application.usecase;

import com.example.limitedticketing.member.application.usecase.dto.query.EnsureMemberPurchasableQuery;

public interface EnsureMemberPurchasableUseCase {

    void ensurePurchasable(EnsureMemberPurchasableQuery query);
}
