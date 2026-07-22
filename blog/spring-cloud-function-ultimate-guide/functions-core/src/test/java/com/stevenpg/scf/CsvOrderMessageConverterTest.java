package com.stevenpg.scf;

import com.stevenpg.scf.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the custom {@code text/csv} converter, exercised directly so its
 * correctness is proven independently of how any surface wires it in.
 */
class CsvOrderMessageConverterTest {

    private final CsvOrderMessageConverter converter = new CsvOrderMessageConverter();

    @Test
    void parsesCsvPayloadIntoOrder() {
        Message<byte[]> message = MessageBuilder
                .withPayload("ord-1,cust-alice,199.99,USD,3".getBytes(StandardCharsets.UTF_8))
                .setHeader(MessageHeaders.CONTENT_TYPE, new MimeType("text", "csv"))
                .build();

        Order order = (Order) converter.fromMessage(message, Order.class);

        assertThat(order).isNotNull();
        assertThat(order.orderId()).isEqualTo("ord-1");
        assertThat(order.customerId()).isEqualTo("cust-alice");
        assertThat(order.amount()).isEqualByComparingTo(new BigDecimal("199.99"));
        assertThat(order.currency()).isEqualTo("USD");
        assertThat(order.itemCount()).isEqualTo(3);
    }

    @Test
    void writesOrderBackToCsv() {
        Order order = new Order("ord-2", "cust-bob", new BigDecimal("42.00"), "USD", 1);

        Message<?> message = converter.toMessage(order,
                new MessageHeaders(java.util.Map.of(MessageHeaders.CONTENT_TYPE, new MimeType("text", "csv"))));

        assertThat(message).isNotNull();
        String csv = new String((byte[]) message.getPayload(), StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("ord-2,cust-bob,42.00,USD,1");
    }
}
