package com.qrmenu.kartapay;

import org.springframework.stereotype.Service;

/**
 * Seul fournisseur de paiement du MVP : aucune dépendance Stripe, aucune clé API, aucun
 * appel réseau. Le paiement créé reste {@code PENDING} indéfiniment — il n'est jamais
 * marqué {@code PAID} automatiquement, ce serait mentir sur un encaissement qui n'a pas
 * eu lieu.
 */
@Service
public class NoPaymentProvider implements PaymentService {

    private final PaymentRepository paymentRepository;

    public NoPaymentProvider(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Payment createPending(Order order) {
        Payment payment = new Payment(
                order.getId(),
                order.getTotalCents(),
                order.getCurrency(),
                PaymentProviderType.NONE);
        return paymentRepository.save(payment);
    }
}
