package com.stevenpg.ecommerce.payments;

import java.util.UUID;

public record PaymentCompletedEvent(UUID orderId, UUID paymentId) {}
