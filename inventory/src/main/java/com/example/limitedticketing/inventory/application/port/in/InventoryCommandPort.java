package com.example.limitedticketing.inventory.application.port.in;

import com.example.limitedticketing.inventory.application.port.in.dto.ConfirmInventoryReservationInput;
import com.example.limitedticketing.inventory.application.port.in.dto.InitializeInventoryInput;
import com.example.limitedticketing.inventory.application.port.in.dto.ReleaseInventoryReservationInput;
import com.example.limitedticketing.inventory.application.port.in.dto.ReserveInventoryInput;

public interface InventoryCommandPort {

    void initialize(InitializeInventoryInput input);

    void reserve(ReserveInventoryInput input);

    void confirmReservation(ConfirmInventoryReservationInput input);

    void releaseReservation(ReleaseInventoryReservationInput input);
}
