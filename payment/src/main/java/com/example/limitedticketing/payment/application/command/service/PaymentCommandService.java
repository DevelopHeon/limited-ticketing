package com.example.limitedticketing.payment.application.command.service;

import com.example.limitedticketing.inventory.application.port.in.InventoryCommandFacade;
import com.example.limitedticketing.inventory.application.port.in.dto.ConfirmInventoryReservationInput;
import com.example.limitedticketing.payment.application.command.dto.ApprovePaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.CreatePaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.FailPaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.PaymentAttemptResult;
import com.example.limitedticketing.payment.application.support.PaymentFinder;
import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.payment.domain.model.Payment;
import com.example.limitedticketing.payment.domain.repository.IdempotentPayment;
import com.example.limitedticketing.payment.domain.repository.PaymentRepository;
import com.example.limitedticketing.purchase.application.port.in.PurchasePaymentFacade;
import com.example.limitedticketing.purchase.application.port.in.dto.FindPurchaseForPaymentInput;
import com.example.limitedticketing.purchase.application.port.in.dto.MarkPurchasePaidInput;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchasePaymentTarget;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchasePaymentStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final PaymentFinder paymentFinder;
    private final PurchasePaymentFacade purchasePaymentFacade;
    private final InventoryCommandFacade inventoryCommandFacade;
    private final Clock clock;

    public PaymentCommandService(
        PaymentRepository paymentRepository,
        PaymentFinder paymentFinder,
        PurchasePaymentFacade purchasePaymentFacade,
        InventoryCommandFacade inventoryCommandFacade,
        Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentFinder = paymentFinder;
        this.purchasePaymentFacade = purchasePaymentFacade;
        this.inventoryCommandFacade = inventoryCommandFacade;
        this.clock = clock;
    }

    @Transactional
    public PaymentAttemptResult createPayment(CreatePaymentCommand command) {
        Payment existing = paymentRepository.findByPaymentRequestKey(command.paymentRequestKey()).orElse(null);
        if (existing != null) {
            return paymentAttemptResultOf(existing, command.memberId(), command.purchaseId());
        }

        PurchasePaymentTarget purchase = getPendingPurchase(
            command.memberId(),
            command.purchaseId(),
            LocalDateTime.now(clock)
        );
        IdempotentPayment savedPayment = paymentRepository.saveIfAbsent(
            Payment.request(purchase.purchaseId(), command.paymentRequestKey(), purchase.amount())
        );
        Payment payment = savedPayment.payment();

        return paymentAttemptResultOf(payment, command.memberId(), command.purchaseId());
    }

    @Transactional
    public PaymentAttemptResult approve(ApprovePaymentCommand command) {
        Payment payment = paymentFinder.getRequired(command.paymentId());
        PurchasePaymentTarget purchase = getPendingPurchase(
            command.memberId(),
            payment.getPurchaseId(),
            LocalDateTime.now(clock)
        );

        payment.approve(LocalDateTime.now(clock));
        purchasePaymentFacade.markPaid(new MarkPurchasePaidInput(purchase.purchaseId()));
        inventoryCommandFacade.confirmReservation(
            new ConfirmInventoryReservationInput(purchase.saleProductId(), purchase.quantity())
        );
        paymentRepository.save(payment);

        return PaymentAttemptResult.of(payment);
    }

    @Transactional
    public PaymentAttemptResult fail(FailPaymentCommand command) {
        Payment payment = paymentFinder.getRequired(command.paymentId());
        PurchasePaymentTarget purchase = findPurchase(payment.getPurchaseId());
        ensurePurchaseMember(command.memberId(), purchase);

        payment.fail();
        paymentRepository.save(payment);

        return PaymentAttemptResult.of(payment);
    }

    private PurchasePaymentTarget getPendingPurchase(Long memberId, Long purchaseId, LocalDateTime now) {
        PurchasePaymentTarget purchase = findPurchase(purchaseId);
        ensurePurchaseMember(memberId, purchase);

        if (purchase.status() != PurchasePaymentStatus.PENDING_PAYMENT) {
            throw PaymentException.invalidState("Only pending purchases can be paid");
        }
        if (!now.isBefore(purchase.expiresAt())) {
            throw PaymentException.reservationExpired();
        }

        return purchase;
    }

    private PurchasePaymentTarget findPurchase(Long purchaseId) {
        return purchasePaymentFacade.findPaymentTarget(new FindPurchaseForPaymentInput(purchaseId));
    }

    private void ensurePurchaseMember(Long memberId, PurchasePaymentTarget purchase) {
        if (!purchase.memberId().equals(memberId)) {
            throw PaymentException.ownershipViolation();
        }
    }

    private PaymentAttemptResult paymentAttemptResultOf(Payment payment, Long memberId, Long purchaseId) {
        if (!payment.getPurchaseId().equals(purchaseId)) {
            throw PaymentException.requestKeyReuse();
        }

        PurchasePaymentTarget purchase = findPurchase(purchaseId);
        ensurePurchaseMember(memberId, purchase);
        return PaymentAttemptResult.of(payment);
    }
}
