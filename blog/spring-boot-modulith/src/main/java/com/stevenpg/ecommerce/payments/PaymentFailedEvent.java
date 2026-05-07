package com.stevenpg.ecommerce.payments;

import java.util.UUID;

public record PaymentFailedEvent(UUID orderId, String reason) {}
