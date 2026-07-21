package com.example.schemaregistry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.schemaregistry.avro.OrderEvent;
import org.springframework.stereotype.Component;

/**
 * Tiny in-memory sink so the demo can show consumed events over HTTP without a
 * database. Not something you would ship — it just makes the flow observable.
 */
@Component
public class OrderEventStore {

    private final List<OrderEvent> received = new CopyOnWriteArrayList<>();

    void add(OrderEvent event) {
        received.add(event);
    }

    public List<OrderEvent> all() {
        return List.copyOf(received);
    }

    public int size() {
        return received.size();
    }

    public void clear() {
        received.clear();
    }
}
