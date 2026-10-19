package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.messaging;

public class InvalidReservationMessageException extends IllegalArgumentException {
    public InvalidReservationMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
