package com.example.limitedticketing.inventory.application.usecase;

import com.example.limitedticketing.inventory.application.usecase.dto.command.ReserveInventoryCommand;

public interface ReserveInventoryUseCase {

    void reserve(ReserveInventoryCommand command);
}
