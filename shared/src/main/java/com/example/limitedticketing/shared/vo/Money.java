package com.example.limitedticketing.shared.vo;

import java.math.BigDecimal;
import java.util.Objects;
import com.example.limitedticketing.shared.exception.SharedValueException;

public final class Money {

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw SharedValueException.invalidMoney();
        }
        this.amount = amount;
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Money money)) {
            return false;
        }
        return amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros());
    }
}
