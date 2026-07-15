package com.example.limitedticketing.purchase.infrastructure.time;

import com.example.limitedticketing.purchase.application.port.out.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SystemClock implements Clock {

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
