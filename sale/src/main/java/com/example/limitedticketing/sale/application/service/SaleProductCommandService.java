package com.example.limitedticketing.sale.application.service;

import com.example.limitedticketing.sale.application.port.out.InventoryInitializationPort;
import com.example.limitedticketing.sale.application.usecase.ChangeSalePeriodUseCase;
import com.example.limitedticketing.sale.application.usecase.CloseSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.CreateSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.OpenSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.dto.command.ChangeSalePeriodCommand;
import com.example.limitedticketing.sale.application.usecase.dto.command.CloseSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.command.CreateSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.command.OpenSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductCommandResult;
import com.example.limitedticketing.sale.domain.exception.SaleException;
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
public class SaleProductCommandService implements
    CreateSaleProductUseCase,
    ChangeSalePeriodUseCase,
    OpenSaleProductUseCase,
    CloseSaleProductUseCase {

    private final ProductRepository productRepository;
    private final SaleProductRepository saleProductRepository;
    private final InventoryInitializationPort inventoryInitializationPort;
    private final Clock clock;

    public SaleProductCommandService(
        ProductRepository productRepository,
        SaleProductRepository saleProductRepository,
        InventoryInitializationPort inventoryInitializationPort,
        Clock clock
    ) {
        this.productRepository = productRepository;
        this.saleProductRepository = saleProductRepository;
        this.inventoryInitializationPort = inventoryInitializationPort;
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
        inventoryInitializationPort.initialize(
            saleProduct.getId(), command.initialInventoryQuantity()
        );

        return SaleProductCommandResult.from(saleProduct);
    }

    @Transactional
    public SaleProductCommandResult changeSalePeriod(ChangeSalePeriodCommand command) {
        SaleProduct saleProduct = getRequiredSaleProduct(command.saleProductId());
        saleProduct.changeSalePeriod(SalePeriod.of(command.saleStartsAt(), command.saleEndsAt()));

        return SaleProductCommandResult.from(saleProductRepository.save(saleProduct));
    }

    @Transactional
    public SaleProductCommandResult open(OpenSaleProductCommand command) {
        SaleProduct saleProduct = getRequiredSaleProduct(command.saleProductId());
        saleProduct.open(LocalDateTime.now(clock));

        return SaleProductCommandResult.from(saleProductRepository.save(saleProduct));
    }

    @Transactional
    public SaleProductCommandResult close(CloseSaleProductCommand command) {
        SaleProduct saleProduct = getRequiredSaleProduct(command.saleProductId());
        saleProduct.close();

        return SaleProductCommandResult.from(saleProductRepository.save(saleProduct));
    }

    private SaleProduct getRequiredSaleProduct(Long id) {
        return saleProductRepository.findById(id).orElseThrow(SaleException::productNotFound);
    }
}
