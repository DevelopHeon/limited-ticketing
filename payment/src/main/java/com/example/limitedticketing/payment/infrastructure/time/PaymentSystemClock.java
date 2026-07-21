package com.example.limitedticketing.payment.infrastructure.time;

import com.example.limitedticketing.payment.application.port.out.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class PaymentSystemClock implements Clock {

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
