package com.example.limitedticketing.payment.application.service;

import com.example.limitedticketing.payment.application.port.out.InventoryConfirmationPort;
import com.example.limitedticketing.payment.application.port.out.PurchasePaymentPort;
import com.example.limitedticketing.payment.application.port.out.dto.PurchasePaymentStatus;
import com.example.limitedticketing.payment.application.port.out.dto.PurchasePaymentTarget;
import com.example.limitedticketing.payment.application.usecase.ApprovePaymentUseCase;
import com.example.limitedticketing.payment.application.usecase.CreatePaymentUseCase;
import com.example.limitedticketing.payment.application.usecase.FailPaymentUseCase;
import com.example.limitedticketing.payment.application.usecase.dto.command.ApprovePaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.command.CreatePaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.command.FailPaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.result.PaymentAttemptResult;
import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.payment.domain.model.Payment;
import com.example.limitedticketing.payment.domain.repository.IdempotentPayment;
import com.example.limitedticketing.payment.domain.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCommandService implements CreatePaymentUseCase, ApprovePaymentUseCase, FailPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PurchasePaymentPort purchasePaymentPort;
    private final InventoryConfirmationPort inventoryConfirmationPort;
    private final Clock clock;

    public PaymentCommandService(
        PaymentRepository paymentRepository,
        PurchasePaymentPort purchasePaymentPort,
        InventoryConfirmationPort inventoryConfirmationPort,
        Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.purchasePaymentPort = purchasePaymentPort;
        this.inventoryConfirmationPort = inventoryConfirmationPort;
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
        Payment payment = getRequiredPayment(command.paymentId());
        PurchasePaymentTarget purchase = getPendingPurchase(
            command.memberId(),
            payment.getPurchaseId(),
            LocalDateTime.now(clock)
        );

        payment.approve(LocalDateTime.now(clock));
        purchasePaymentPort.markPaid(purchase.purchaseId());
        inventoryConfirmationPort.confirmReservation(
            purchase.saleProductId(), purchase.quantity()
        );
        paymentRepository.transitionFromReady(payment);

        return PaymentAttemptResult.of(payment);
    }

    @Transactional
    public PaymentAttemptResult fail(FailPaymentCommand command) {
        Payment payment = getRequiredPayment(command.paymentId());
        PurchasePaymentTarget purchase = findPurchase(payment.getPurchaseId());
        ensurePurchaseMember(command.memberId(), purchase);

        payment.fail();
        paymentRepository.transitionFromReady(payment);

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
        return purchasePaymentPort.findPaymentTarget(purchaseId);
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

    private Payment getRequiredPayment(Long id) {
        return paymentRepository.findById(id).orElseThrow(PaymentException::notFound);
    }
}
