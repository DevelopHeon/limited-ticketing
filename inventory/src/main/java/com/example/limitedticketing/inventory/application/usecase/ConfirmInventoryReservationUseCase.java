package com.example.limitedticketing.inventory.application.usecase;

import com.example.limitedticketing.inventory.application.usecase.dto.command.ConfirmInventoryReservationCommand;

public interface ConfirmInventoryReservationUseCase {

    void confirmReservation(ConfirmInventoryReservationCommand command);
}
