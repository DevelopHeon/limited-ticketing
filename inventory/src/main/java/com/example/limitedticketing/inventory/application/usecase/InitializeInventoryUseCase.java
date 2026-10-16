package com.example.limitedticketing.inventory.application.usecase;

import com.example.limitedticketing.inventory.application.usecase.dto.command.InitializeInventoryCommand;

public interface InitializeInventoryUseCase {

    void initialize(InitializeInventoryCommand command);
}
