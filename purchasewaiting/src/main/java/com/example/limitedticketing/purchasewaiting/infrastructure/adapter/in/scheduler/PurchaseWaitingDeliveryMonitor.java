package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.scheduler;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!api")
public class PurchaseWaitingDeliveryMonitor {
    private static final Logger log = LoggerFactory.getLogger(PurchaseWaitingDeliveryMonitor.class);
    private final MeterRegistry meterRegistry;

    public PurchaseWaitingDeliveryMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${limited-ticketing.kafka.delivery-warning-delay:60000}",
        initialDelayString = "${limited-ticketing.kafka.delivery-warning-delay:60000}")
    public void warnDelayedDeliveries() {
        Gauge age = meterRegistry.find("reservation.dispatched.oldest.seconds").gauge();
        if (age != null && age.value() >= 60) {
            log.warn("Reservation delivery has waited at least 60 seconds; inspect the outbox, broker and consumer before replay");
        }
    }
}
