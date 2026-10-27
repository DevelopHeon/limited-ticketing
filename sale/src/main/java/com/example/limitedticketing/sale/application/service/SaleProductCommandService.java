package com.example.limitedticketing.sale.application.service;

import com.example.limitedticketing.sale.application.port.out.InventoryInitializationPort;
import com.example.limitedticketing.sale.application.port.out.SaleProductReadCachePort;
import com.example.limitedticketing.sale.application.usecase.ChangeSalePeriodUseCase;
import com.example.limitedticketing.sale.application.usecase.CloseSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.CreateSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.OpenSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.dto.command.ChangeSalePeriodCommand;
import com.example.limitedticketing.sale.application.usecase.dto.command.CloseSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.command.CreateSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.command.OpenSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductCommandResult;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SaleProductCommandService implements
    CreateSaleProductUseCase,
    ChangeSalePeriodUseCase,
    OpenSaleProductUseCase,
    CloseSaleProductUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(SaleProductCommandService.class);
    private final ProductRepository productRepository;
    private final SaleProductRepository saleProductRepository;
    private final InventoryInitializationPort inventoryInitializationPort;
    private final Clock clock;
    private final SaleProductReadCachePort saleProductReadCachePort;

    public SaleProductCommandService(
        ProductRepository productRepository,
        SaleProductRepository saleProductRepository,
        InventoryInitializationPort inventoryInitializationPort,
        Clock clock,
        SaleProductReadCachePort saleProductReadCachePort
    ) {
        this.productRepository = productRepository;
        this.saleProductRepository = saleProductRepository;
        this.inventoryInitializationPort = inventoryInitializationPort;
        this.clock = clock;
        this.saleProductReadCachePort = saleProductReadCachePort;
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

        invalidateCacheAfterCommit();
        return SaleProductCommandResult.from(saleProduct);
    }

    @Transactional
    public SaleProductCommandResult changeSalePeriod(ChangeSalePeriodCommand command) {
        SaleProduct saleProduct = saleProductRepository.getRequiredById(command.saleProductId());
        saleProduct.changeSalePeriod(SalePeriod.of(command.saleStartsAt(), command.saleEndsAt()));

        SaleProduct saved = saleProductRepository.save(saleProduct);
        invalidateCacheAfterCommit();
        return SaleProductCommandResult.from(saved);
    }

    @Transactional
    public SaleProductCommandResult open(OpenSaleProductCommand command) {
        SaleProduct saleProduct = saleProductRepository.getRequiredById(command.saleProductId());
        saleProduct.open(LocalDateTime.now(clock));

        SaleProduct saved = saleProductRepository.save(saleProduct);
        invalidateCacheAfterCommit();
        return SaleProductCommandResult.from(saved);
    }

    @Transactional
    public SaleProductCommandResult close(CloseSaleProductCommand command) {
        SaleProduct saleProduct = saleProductRepository.getRequiredById(command.saleProductId());
        saleProduct.close();

        SaleProduct saved = saleProductRepository.save(saleProduct);
        invalidateCacheAfterCommit();
        return SaleProductCommandResult.from(saved);
    }

    private void invalidateCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidateCache();
                }
            });
        } else {
            invalidateCache();
        }
    }

    private void invalidateCache() {
        try {
            saleProductReadCachePort.invalidate();
        } catch (RuntimeException exception) {
            LOG.warn("판매 변경 commit 후 캐시 무효화 실패: TTL로 복구", exception);
        }
    }
}
