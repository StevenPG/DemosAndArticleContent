package com.stevenpg.ecommerce;

import com.stevenpg.ecommerce.inventory.StockReservedEvent;
import com.stevenpg.ecommerce.inventory.StockShortageEvent;
import com.stevenpg.ecommerce.orders.OrderPlacedEvent;
import com.stevenpg.ecommerce.payments.PaymentCompletedEvent;
import com.stevenpg.ecommerce.payments.PaymentFailedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Structural verification of the Spring Modulith architecture.
 *
 * These tests are purely static — they analyse bytecode with ArchUnit and never
 * start a Spring application context.  They run fast and catch architectural
 * drift the moment a developer adds an illegal import.
 *
 * A failure here means one of:
 *   1. A class in module A imported something from another module's {@code internal} package.
 *   2. A module declared a dependency that its {@code allowedDependencies} does not permit.
 *   3. A circular dependency was introduced between modules.
 */
class ModularityTests {

    // Bytecode analysis is expensive — compute once and share across all tests.
    static final ApplicationModules modules = ApplicationModules.of(EcommerceApplication.class);

    // =========================================================================
    // 1.  Core structural guard
    //     verify() is the single source of truth.  All other tests in this class
    //     make the constraints explicit but verify() is what enforces them.
    // =========================================================================

    @Test
    void verifyNoIllegalCrossModuleAccess() {
        // Throws a detailed violation report if any module boundary is breached.
        modules.verify();
    }

    @Test
    void verifyDoesNotThrow() {
        assertThatCode(modules::verify)
                .as("Spring Modulith detected architectural violations — see console output")
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // 2.  Module inventory
    // =========================================================================

    @Test
    void exactlyFourModulesAreDetected() {
        assertThat(modules.stream())
                .as("Expected exactly four modules: catalog, orders, inventory, payments")
                .hasSize(4);
    }

    @ParameterizedTest(name = "module ''{0}'' is detected")
    @ValueSource(strings = {"catalog", "orders", "inventory", "payments"})
    void expectedModuleExists(String moduleName) {
        assertThat(modules.getModuleByName(moduleName))
                .as("Module '%s' must be registered with Spring Modulith", moduleName)
                .isPresent();
    }

    // =========================================================================
    // 3.  Display names  (declared in each package-info.java @ApplicationModule)
    //     Human-readable names appear in actuator output and generated diagrams.
    // =========================================================================

    @Test
    void allModulesHaveExplicitDisplayNames() {
        modules.forEach(module ->
                assertThat(module.getDisplayName())
                        .as("Module '%s' must set displayName in @ApplicationModule", module.getIdentifier())
                        .isNotBlank()
        );
    }

    @ParameterizedTest(name = "''{0}'' has display name ''{1}''")
    @CsvSource({
            "catalog,   Catalog",
            "orders,    Orders",
            "inventory, Inventory",
            "payments,  Payments"
    })
    void moduleDisplayNamesMatchDeclarations(String moduleName, String expectedDisplayName) {
        assertThat(modules.getModuleByName(moduleName.strip()))
                .hasValueSatisfying(m ->
                        assertThat(m.getDisplayName()).isEqualTo(expectedDisplayName.strip())
                );
    }

    // =========================================================================
    // 4.  Dependency graph  (acyclic — no cycles are permitted)
    //
    //     catalog  (leaf — no deps)
    //        ↑
    //     orders   → catalog
    //        ↑
    //     inventory → orders
    //
    //     payments  → inventory
    //     payments  → orders   (direct call to confirm/fail the order)
    //
    //   ApplicationModuleDependencies.uniqueModules() deduplicates repeated edges
    //   (e.g. multiple classes in payments importing from orders) into one entry
    //   per target module — exactly what we want for graph assertions.
    //
    //   Spring Modulith 2.x API note:
    //     getDependencies()   → deprecated; use getDirectDependencies()
    //     module.getName()    → deprecated; use module.getIdentifier().toString()
    // =========================================================================

    @Test
    void catalogHasNoExternalModuleDependencies() {
        var deps = modules.getModuleByName("catalog").orElseThrow()
                .getDirectDependencies(modules);

        assertThat(deps.isEmpty())
                .as("Catalog must not depend on any other module")
                .isTrue();
    }

    @Test
    void ordersOnlyDependsOnCatalog() {
        List<String> depNames = modules.getModuleByName("orders").orElseThrow()
                .getDirectDependencies(modules)
                .uniqueModules()
                .map(m -> m.getIdentifier().toString())
                .toList();

        assertThat(depNames)
                .as("Orders may only depend on catalog")
                .containsExactlyInAnyOrder("catalog");
    }

    @Test
    void inventoryOnlyDependsOnOrders() {
        List<String> depNames = modules.getModuleByName("inventory").orElseThrow()
                .getDirectDependencies(modules)
                .uniqueModules()
                .map(m -> m.getIdentifier().toString())
                .toList();

        assertThat(depNames)
                .as("Inventory may only depend on orders")
                .containsExactlyInAnyOrder("orders");
    }

    @Test
    void paymentsOnlyDependsOnInventoryAndOrders() {
        List<String> depNames = modules.getModuleByName("payments").orElseThrow()
                .getDirectDependencies(modules)
                .uniqueModules()
                .map(m -> m.getIdentifier().toString())
                .toList();

        assertThat(depNames)
                .as("Payments may only depend on inventory and orders")
                .containsExactlyInAnyOrder("inventory", "orders");
    }

    @Test
    void noDependencyPointsBackAtOrders() {
        // catalog has no deps at all, so it cannot create a cycle back to orders.
        var catalogDeps = modules.getModuleByName("catalog").orElseThrow()
                .getDirectDependencies(modules);

        assertThat(catalogDeps.containsModuleNamed("orders"))
                .as("Catalog must not depend on orders (would form a cycle)")
                .isFalse();
    }

    @Test
    void moduleDependencyGraphContainsNoDirectCyclesBetweenAnyPair() {
        // For every pair (A, B) where A depends on B, verify B does not also
        // depend on A.  A full transitive cycle check is covered by verify().
        modules.forEach(moduleA -> {
            var idA = moduleA.getIdentifier().toString();
            moduleA.getDirectDependencies(modules).uniqueModules().forEach(moduleB -> {
                var idB = moduleB.getIdentifier().toString();
                boolean bDependsOnA = moduleB.getDirectDependencies(modules).containsModuleNamed(idA);
                assertThat(bDependsOnA)
                        .as("Cycle detected: %s → %s and %s → %s", idA, idB, idB, idA)
                        .isFalse();
            });
        });
    }

    // =========================================================================
    // 5.  Event ownership
    //     Each domain event lives in the module that publishes it.
    //     Consumers must import from the publisher's public package — never from
    //     their own module or a shared package.
    // =========================================================================

    @Test
    void orderPlacedEventBelongsToOrdersModule() {
        assertThat(modules.getModuleByType(OrderPlacedEvent.class))
                .as("OrderPlacedEvent must be owned by the orders module")
                .hasValueSatisfying(m ->
                        assertThat(m.getIdentifier().toString()).isEqualTo("orders"));
    }

    @Test
    void stockEventsBelongToInventoryModule() {
        assertThat(modules.getModuleByType(StockReservedEvent.class))
                .as("StockReservedEvent must be owned by the inventory module")
                .hasValueSatisfying(m ->
                        assertThat(m.getIdentifier().toString()).isEqualTo("inventory"));

        assertThat(modules.getModuleByType(StockShortageEvent.class))
                .as("StockShortageEvent must be owned by the inventory module")
                .hasValueSatisfying(m ->
                        assertThat(m.getIdentifier().toString()).isEqualTo("inventory"));
    }

    @Test
    void paymentEventsBelongToPaymentsModule() {
        assertThat(modules.getModuleByType(PaymentCompletedEvent.class))
                .as("PaymentCompletedEvent must be owned by the payments module")
                .hasValueSatisfying(m ->
                        assertThat(m.getIdentifier().toString()).isEqualTo("payments"));

        assertThat(modules.getModuleByType(PaymentFailedEvent.class))
                .as("PaymentFailedEvent must be owned by the payments module")
                .hasValueSatisfying(m ->
                        assertThat(m.getIdentifier().toString()).isEqualTo("payments"));
    }

    // =========================================================================
    // 6.  Documentation generation
    //     Writes AsciiDoc + PlantUML diagrams to build/spring-modulith-docs/.
    //     Run once and commit the output as living architecture documentation.
    // =========================================================================

    @Test
    void generateModuleDocumentation() {
        new Documenter(modules)
                .writeDocumentation()
                .writeIndividualModulesAsPlantUml();
    }
}
