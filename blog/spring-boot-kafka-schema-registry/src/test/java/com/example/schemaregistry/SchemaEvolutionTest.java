package com.example.schemaregistry;

import java.util.UUID;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The heart of the demo: what the Schema Registry actually enforces.
 *
 * <p>Talking to the registry directly (instead of through a producer) lets us
 * assert the compatibility rules without touching Kafka. Every test registers
 * v1 under a fresh subject with BACKWARD compatibility — the same mode the
 * registry uses by default — and then tries to evolve it.
 */
class SchemaEvolutionTest extends AbstractKafkaSchemaRegistryTest {

    /** v1 — the schema the application ships with (mirrors src/main/avro/OrderEvent.avsc). */
    private static final String V1 = """
            {
              "type": "record",
              "name": "OrderEvent",
              "namespace": "com.example.schemaregistry.avro",
              "fields": [
                { "name": "orderId", "type": "string" },
                { "name": "customerId", "type": "string" },
                { "name": "amount", "type": "double" },
                { "name": "currency", "type": "string", "default": "USD" },
                { "name": "status",
                  "type": { "type": "enum", "name": "OrderStatus",
                            "symbols": ["PLACED","PAID","SHIPPED","CANCELLED"], "default": "PLACED" },
                  "default": "PLACED" },
                { "name": "createdAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
              ]
            }
            """;

    /** v2 — adds an OPTIONAL field with a default. Safe under BACKWARD compatibility. */
    private static final String V2_ADD_OPTIONAL_FIELD = """
            {
              "type": "record",
              "name": "OrderEvent",
              "namespace": "com.example.schemaregistry.avro",
              "fields": [
                { "name": "orderId", "type": "string" },
                { "name": "customerId", "type": "string" },
                { "name": "amount", "type": "double" },
                { "name": "currency", "type": "string", "default": "USD" },
                { "name": "status",
                  "type": { "type": "enum", "name": "OrderStatus",
                            "symbols": ["PLACED","PAID","SHIPPED","CANCELLED"], "default": "PLACED" },
                  "default": "PLACED" },
                { "name": "createdAt", "type": { "type": "long", "logicalType": "timestamp-millis" } },
                { "name": "loyaltyTier", "type": ["null", "string"], "default": null }
              ]
            }
            """;

    /** Broken — changes the type of an existing field. Rejected under BACKWARD compatibility. */
    private static final String INCOMPATIBLE_TYPE_CHANGE = """
            {
              "type": "record",
              "name": "OrderEvent",
              "namespace": "com.example.schemaregistry.avro",
              "fields": [
                { "name": "orderId", "type": "string" },
                { "name": "customerId", "type": "string" },
                { "name": "amount", "type": "string" },
                { "name": "currency", "type": "string", "default": "USD" },
                { "name": "status",
                  "type": { "type": "enum", "name": "OrderStatus",
                            "symbols": ["PLACED","PAID","SHIPPED","CANCELLED"], "default": "PLACED" },
                  "default": "PLACED" },
                { "name": "createdAt", "type": { "type": "long", "logicalType": "timestamp-millis" } }
              ]
            }
            """;

    private SchemaRegistryClient client;
    private String subject;

    @BeforeEach
    void setUp() throws Exception {
        client = new CachedSchemaRegistryClient(schemaRegistryUrl(), 100);
        // Fresh subject per test so shared containers don't leak state between tests.
        subject = "orders-" + UUID.randomUUID() + "-value";
        client.register(subject, new AvroSchema(V1));
        client.updateCompatibility(subject, "BACKWARD");
    }

    @Test
    void addingAnOptionalFieldIsBackwardCompatible() throws Exception {
        AvroSchema v2 = new AvroSchema(V2_ADD_OPTIONAL_FIELD);

        // The registry says yes...
        assertThat(client.testCompatibility(subject, v2)).isTrue();

        // ...and accepts the registration as version 2.
        int newId = client.register(subject, v2);
        assertThat(newId).isPositive();
        assertThat(client.getAllVersions(subject)).containsExactly(1, 2);
    }

    @Test
    void changingAFieldTypeIsRejected() {
        AvroSchema broken = new AvroSchema(INCOMPATIBLE_TYPE_CHANGE);

        // A dry run reports the change is not compatible...
        assertThat(compatible(broken)).isFalse();

        // ...and an actual registration is refused with HTTP 409 Conflict.
        assertThatThrownBy(() -> client.register(subject, broken))
                .isInstanceOf(RestClientException.class)
                .satisfies(ex -> assertThat(((RestClientException) ex).getStatus()).isEqualTo(409));
    }

    private boolean compatible(AvroSchema candidate) {
        try {
            return client.testCompatibility(subject, candidate);
        } catch (Exception e) {
            throw new IllegalStateException("compatibility check failed", e);
        }
    }
}
