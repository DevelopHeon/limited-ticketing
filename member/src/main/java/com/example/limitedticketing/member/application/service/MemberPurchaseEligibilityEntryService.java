package com.example.limitedticketing.member.application.service;

import com.example.limitedticketing.member.application.port.in.MemberPurchaseEligibilityPort;
import com.example.limitedticketing.member.application.port.in.dto.EnsureMemberPurchasableInput;
import com.example.limitedticketing.member.application.usecase.EnsureMemberPurchasableUseCase;
import com.example.limitedticketing.member.application.usecase.dto.query.EnsureMemberPurchasableQuery;
import org.springframework.stereotype.Service;

@Service
public class MemberPurchaseEligibilityEntryService implements MemberPurchaseEligibilityPort {

    private final EnsureMemberPurchasableUseCase ensureMemberPurchasableUseCase;

    public MemberPurchaseEligibilityEntryService(EnsureMemberPurchasableUseCase ensureMemberPurchasableUseCase) {
        this.ensureMemberPurchasableUseCase = ensureMemberPurchasableUseCase;
    }

    public void ensurePurchasable(EnsureMemberPurchasableInput input) {
        ensureMemberPurchasableUseCase.ensurePurchasable(new EnsureMemberPurchasableQuery(input.memberId()));
    }
}
