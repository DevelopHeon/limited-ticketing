package com.example.limitedticketing.purchase.application.port.out;

import java.time.LocalDateTime;

public interface Clock {

    LocalDateTime now();
}
