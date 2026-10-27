package com.example.limitedticketing.member.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.limitedticketing.member.application.usecase.dto.query.EnsureMemberPurchasableQuery;
import com.example.limitedticketing.member.domain.exception.MemberException;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 구매 자격 조회의 존재 조건")
class MemberQueryServiceTest {
    @Mock private MemberRepository memberRepository;

    @Test
    @DisplayName("존재하지 않는 회원의 구매 자격을 승인하지 않고 회원 없음으로 응답한다")
    void missingMemberCannotBeApprovedForPurchase() {
        when(memberRepository.getRequiredById(1L)).thenCallRealMethod();

        assertThatThrownBy(() -> new MemberQueryService(memberRepository)
            .ensurePurchasable(new EnsureMemberPurchasableQuery(1L)))
            .isInstanceOf(MemberException.class).extracting("code").isEqualTo("MEMBER_NOT_FOUND");
    }
}
