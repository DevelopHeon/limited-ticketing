package com.example.limitedticketing.shared.vo;

import java.time.LocalDateTime;

public final class SalePeriod {

    private final LocalDateTime startsAt;
    private final LocalDateTime endsAt;

    private SalePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("Sale start time must be before end time");
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
