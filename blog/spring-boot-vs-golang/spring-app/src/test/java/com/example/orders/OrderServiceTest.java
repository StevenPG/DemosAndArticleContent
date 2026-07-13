package com.example.orders;

import com.example.orders.clients.InventoryClient;
import com.example.orders.clients.PaymentClient;
import com.example.orders.domain.Order;
import com.example.orders.domain.OrderRepository;
import com.example.orders.domain.OrderService;
import com.example.orders.domain.OrderStatus;
import com.example.orders.messaging.OrderEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test — no Spring context, no containers. The service is
 * constructor-injected, so it tests like any other Java class.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository repository;
    @Mock
    PaymentClient paymentClient;
    @Mock
    InventoryClient inventoryClient;
    @Mock
    OrderEventPublisher eventPublisher;

    @InjectMocks
    OrderService orderService;

    @Test
    void createOrderPersistsAndPublishesEvent() {
        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrder("jane@example.com", "widget", 2, 1999);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(eventPublisher).publishOrderCreated(order.getId());
    }

    @Test
    void processOrderCompletesWhenDownstreamCallsSucceed() {
        Order order = new Order("jane@example.com", "widget", 2, 1999);
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentClient.charge(order.getId(), 1999))
                .thenReturn(new PaymentClient.PaymentResult("pay-1", "CHARGED"));

        orderService.processOrder(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getPaymentId()).isEqualTo("pay-1");
        verify(inventoryClient).reserve(order.getId(), "widget", 2);
    }

    @Test
    void processOrderMarksFailedWhenPaymentThrows() {
        Order order = new Order("jane@example.com", "widget", 2, 1999);
        when(repository.findById(order.getId())).thenReturn(Optional.of(order));
        when(paymentClient.charge(any(), anyLong())).thenThrow(new RuntimeException("card declined"));

        orderService.processOrder(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureReason()).isEqualTo("card declined");
    }
}
