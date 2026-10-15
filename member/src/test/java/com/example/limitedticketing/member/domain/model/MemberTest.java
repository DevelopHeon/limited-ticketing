package com.example.limitedticketing.member.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.limitedticketing.member.domain.exception.MemberException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("회원의 구매 자격")
class MemberTest {

    @Test
    @DisplayName("신규 가입한 활성 회원은 구매할 수 있다")
    void newlyRegisteredMemberCanPurchase() {
        Member member = Member.register("member-1", "회원");

        assertThatCode(member::ensurePurchasable).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @EnumSource(value = MemberStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("차단되거나 탈퇴한 회원은 구매할 수 없다")
    void inactiveMemberCannotPurchase(MemberStatus status) {
        Member member = Member.reconstitute(1L, "member-1", "회원", status);

        assertThatThrownBy(member::ensurePurchasable)
            .isInstanceOf(MemberException.class)
            .extracting("code").isEqualTo("MEMBER_NOT_PURCHASABLE");
    }
}
