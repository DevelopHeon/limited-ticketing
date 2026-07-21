package com.example.limitedticketing.payment.application.port.out;

import java.time.LocalDateTime;

public interface Clock {

    LocalDateTime now();
}
