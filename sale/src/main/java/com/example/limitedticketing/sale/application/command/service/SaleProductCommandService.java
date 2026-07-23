package com.example.limitedticketing.sale.application.command.service;

import com.example.limitedticketing.inventory.application.port.in.InventoryCommandFacade;
import com.example.limitedticketing.inventory.application.port.in.dto.InitializeInventoryInput;
import com.example.limitedticketing.sale.application.command.dto.ChangeSalePeriodCommand;
import com.example.limitedticketing.sale.application.command.dto.CloseSaleProductCommand;
import com.example.limitedticketing.sale.application.command.dto.CreateSaleProductCommand;
import com.example.limitedticketing.sale.application.command.dto.OpenSaleProductCommand;
import com.example.limitedticketing.sale.application.command.dto.SaleProductCommandResult;
import com.example.limitedticketing.sale.application.support.SaleProductFinder;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleProductCommandService {

    private final ProductRepository productRepository;
    private final SaleProductRepository saleProductRepository;
    private final SaleProductFinder saleProductFinder;
    private final InventoryCommandFacade inventoryCommandFacade;
    private final Clock clock;

    public SaleProductCommandService(
        ProductRepository productRepository,
        SaleProductRepository saleProductRepository,
        SaleProductFinder saleProductFinder,
        InventoryCommandFacade inventoryCommandFacade,
        Clock clock
    ) {
        this.productRepository = productRepository;
        this.saleProductRepository = saleProductRepository;
        this.saleProductFinder = saleProductFinder;
        this.inventoryCommandFacade = inventoryCommandFacade;
        this.clock = clock;
    }

    @Transactional
    public SaleProductCommandResult create(CreateSaleProductCommand command) {
        Product product = productRepository.save(Product.create(command.productName(), command.benefitDescription()));
        SaleProduct saleProduct = saleProductRepository.save(SaleProduct.createDraft(
            product.getId(),
            Money.of(command.salePrice()),
            SalePeriod.of(command.saleStartsAt(), command.saleEndsAt())
        ));
        inventoryCommandFacade.initialize(
            new InitializeInventoryInput(saleProduct.getId(), command.initialInventoryQuantity())
        );

        return SaleProductCommandResult.from(saleProduct);
    }

    @Transactional
    public SaleProductCommandResult changeSalePeriod(ChangeSalePeriodCommand command) {
        SaleProduct saleProduct = saleProductFinder.getRequired(command.saleProductId());
        saleProduct.changeSalePeriod(SalePeriod.of(command.saleStartsAt(), command.saleEndsAt()));

        return SaleProductCommandResult.from(saleProductRepository.save(saleProduct));
    }

    @Transactional
    public SaleProductCommandResult open(OpenSaleProductCommand command) {
        SaleProduct saleProduct = saleProductFinder.getRequired(command.saleProductId());
        saleProduct.open(LocalDateTime.now(clock));

        return SaleProductCommandResult.from(saleProductRepository.save(saleProduct));
    }

    @Transactional
    public SaleProductCommandResult close(CloseSaleProductCommand command) {
        SaleProduct saleProduct = saleProductFinder.getRequired(command.saleProductId());
        saleProduct.close();

        return SaleProductCommandResult.from(saleProductRepository.save(saleProduct));
    }
}
