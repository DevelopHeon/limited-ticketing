package com.example.limitedticketing.shared.vo;

import java.time.LocalDateTime;
import com.example.limitedticketing.shared.exception.SharedValueException;

public final class SalePeriod {

    private final LocalDateTime startsAt;
    private final LocalDateTime endsAt;

    private SalePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            throw SharedValueException.invalidSalePeriod();
        }
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public static SalePeriod of(LocalDateTime startsAt, LocalDateTime endsAt) {
        return new SalePeriod(startsAt, endsAt);
    }

    public boolean includes(LocalDateTime time) {
        return !time.isBefore(startsAt) && time.isBefore(endsAt);
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }
}
