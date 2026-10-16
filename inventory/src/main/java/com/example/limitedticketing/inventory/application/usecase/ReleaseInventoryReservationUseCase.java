package com.example.limitedticketing.inventory.application.usecase;

import com.example.limitedticketing.inventory.application.usecase.dto.command.ReleaseInventoryReservationCommand;

public interface ReleaseInventoryReservationUseCase {

    void releaseReservation(ReleaseInventoryReservationCommand command);
}
