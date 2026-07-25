package com.stevenpg.scf;

import com.stevenpg.scf.model.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * A custom {@link org.springframework.messaging.converter.MessageConverter} that
 * teaches the pipeline to speak {@code text/csv} in addition to JSON.
 *
 * <p>Spring Cloud Function converts payloads with a composite of
 * {@code MessageConverter}s keyed by {@code contentType}. Declaring a converter
 * bean (see {@link ConverterConfig}) adds a new content type the SAME functions
 * can be invoked with — send {@code text/csv} to {@code enrichOrder} and it just
 * works, no change to the function. This is how you support a legacy wire format
 * without touching business logic.
 *
 * <p>CSV shape (one order per message):
 * {@code orderId,customerId,amount,currency,itemCount}
 */
public class CsvOrderMessageConverter extends AbstractMessageConverter {

    public CsvOrderMessageConverter() {
        super(new MimeType("text", "csv"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return Order.class.equals(clazz);
    }

    @Override
    protected Object convertFromInternal(Message<?> message, Class<?> targetClass,
                                         Object conversionHint) {
        String csv = asString(message.getPayload()).trim();
        String[] f = csv.split(",");
        if (f.length != 5) {
            throw new IllegalArgumentException(
                    "expected 5 CSV fields (orderId,customerId,amount,currency,itemCount) but got: " + csv);
        }
        return new Order(
                f[0].trim(),
                f[1].trim(),
                new BigDecimal(f[2].trim()),
                f[3].trim(),
                Integer.parseInt(f[4].trim()));
    }

    @Override
    protected Object convertToInternal(Object payload, MessageHeaders headers, Object conversionHint) {
        Order o = (Order) payload;
        String csv = String.join(",",
                o.orderId(), o.customerId(), o.amount().toPlainString(), o.currency(), Integer.toString(o.itemCount()));
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    private static String asString(Object payload) {
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return payload.toString();
    }
}
