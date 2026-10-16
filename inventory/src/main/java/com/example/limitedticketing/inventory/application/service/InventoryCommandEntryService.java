package com.example.limitedticketing.inventory.application.service;

import com.example.limitedticketing.inventory.application.port.in.InventoryCommandPort;
import com.example.limitedticketing.inventory.application.port.in.dto.ConfirmInventoryReservationInput;
import com.example.limitedticketing.inventory.application.port.in.dto.InitializeInventoryInput;
import com.example.limitedticketing.inventory.application.port.in.dto.ReleaseInventoryReservationInput;
import com.example.limitedticketing.inventory.application.port.in.dto.ReserveInventoryInput;
import com.example.limitedticketing.inventory.application.usecase.ConfirmInventoryReservationUseCase;
import com.example.limitedticketing.inventory.application.usecase.InitializeInventoryUseCase;
import com.example.limitedticketing.inventory.application.usecase.ReleaseInventoryReservationUseCase;
import com.example.limitedticketing.inventory.application.usecase.ReserveInventoryUseCase;
import com.example.limitedticketing.inventory.application.usecase.dto.command.ConfirmInventoryReservationCommand;
import com.example.limitedticketing.inventory.application.usecase.dto.command.InitializeInventoryCommand;
import com.example.limitedticketing.inventory.application.usecase.dto.command.ReleaseInventoryReservationCommand;
import com.example.limitedticketing.inventory.application.usecase.dto.command.ReserveInventoryCommand;
import org.springframework.stereotype.Service;

@Service
public class InventoryCommandEntryService implements InventoryCommandPort {

    private final InitializeInventoryUseCase initializeInventoryUseCase;
    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final ConfirmInventoryReservationUseCase confirmInventoryReservationUseCase;
    private final ReleaseInventoryReservationUseCase releaseInventoryReservationUseCase;

    public InventoryCommandEntryService(
        InitializeInventoryUseCase initializeInventoryUseCase,
        ReserveInventoryUseCase reserveInventoryUseCase,
        ConfirmInventoryReservationUseCase confirmInventoryReservationUseCase,
        ReleaseInventoryReservationUseCase releaseInventoryReservationUseCase
    ) {
        this.initializeInventoryUseCase = initializeInventoryUseCase;
        this.reserveInventoryUseCase = reserveInventoryUseCase;
        this.confirmInventoryReservationUseCase = confirmInventoryReservationUseCase;
        this.releaseInventoryReservationUseCase = releaseInventoryReservationUseCase;
    }

    public void initialize(InitializeInventoryInput input) {
        initializeInventoryUseCase.initialize(new InitializeInventoryCommand(input.saleProductId(), input.initialQuantity()));
    }

    public void reserve(ReserveInventoryInput input) {
        reserveInventoryUseCase.reserve(new ReserveInventoryCommand(input.saleProductId(), input.quantity()));
    }

    public void confirmReservation(ConfirmInventoryReservationInput input) {
        confirmInventoryReservationUseCase.confirmReservation(
            new ConfirmInventoryReservationCommand(input.saleProductId(), input.quantity())
        );
    }

    public void releaseReservation(ReleaseInventoryReservationInput input) {
        releaseInventoryReservationUseCase.releaseReservation(
            new ReleaseInventoryReservationCommand(input.saleProductId(), input.quantity())
        );
    }
}
