package com.stevenpg.grpc.storefront;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.stevenpg.grpc.inventory.proto.InventoryServiceGrpc;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the client application context: channel configuration parses,
 * stub beans are created, and interceptors register. gRPC channels connect
 * lazily, so this passes without a running inventory-server - end-to-end
 * behaviour is covered by scripts/demo-requests.sh against live services.
 */
@SpringBootTest
class StorefrontClientApplicationTest {

    @Autowired
    private InventoryServiceGrpc.InventoryServiceBlockingStub blockingStub;

    @Autowired
    private InventoryServiceGrpc.InventoryServiceStub asyncStub;

    @Test
    void grpcStubBeansAreConfigured() {
        assertThat(blockingStub).isNotNull();
        assertThat(asyncStub).isNotNull();
    }
}
