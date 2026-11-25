package com.example.valhallajpa;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The mappings you'd actually want: value records as embeddables, as an
 * @EmbeddedId, and behind an AttributeConverter, used through Spring Data JPA.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ValueClassMappingTest {

    @Autowired
    PurchaseOrderRepository orders;

    @Autowired
    EntityManager em;

    @Autowired
    TransactionTemplate tx;

    @Test
    void valueClassesAreReallyValueClasses() {
        assertThat(Money.class.isValue()).isTrue();
        assertThat(OrderId.class.isValue()).isTrue();
        assertThat(Sku.class.isValue()).isTrue();
        assertThat(IdentityMoney.class.isValue()).isFalse();
        // With --enable-preview, the JDK's box classes become value classes too...
        assertThat(Long.class.isValue()).isTrue();
        // ...but UUID and String are still identity classes in this build.
        assertThat(UUID.class.isValue()).isFalse();
        assertThat(String.class.isValue()).isFalse();
        System.out.printf("%nRuntime: %s, Hibernate %s%n",
                Runtime.version(), org.hibernate.Version.getVersionString());
    }

    @Test
    void roundTripsEmbeddedIdEmbeddableAndConverter() {
        Customer customer = tx.execute(s -> {
            Customer c = new Customer("Ada");
            em.persist(c);
            return c;
        });
        OrderId id = OrderId.random();
        orders.save(new PurchaseOrder(id, new Money(1999, "USD"), new Sku("ABC-1"), customer));

        // == on a value object compares its fields with ==. UUID is still an
        // identity class, so an OrderId holding an equal-but-distinct UUID is
        // NOT ==, even though it is equals(). Lookups go through equals(), so
        // findById works either way.
        OrderId sameRef = new OrderId(id.value());
        OrderId lookup = new OrderId(UUID.fromString(id.value().toString()));
        assertThat(sameRef == id).isTrue();
        assertThat(lookup == id).isFalse();
        assertThat(lookup).isEqualTo(id);

        PurchaseOrder loaded = orders.findById(lookup).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getTotal()).isEqualTo(new Money(1999, "USD"));
        // The currency String came back from JDBC as a new String instance, so
        // the loaded Money is equals() but not == to one built from a literal.
        assertThat(loaded.getTotal() == new Money(1999, "USD")).isFalse();
        assertThat(loaded.getSku()).isEqualTo(new Sku("ABC-1"));
    }

    @Test
    void derivedQueriesBindValueClassParameters() {
        OrderId id = OrderId.random();
        orders.save(new PurchaseOrder(id, new Money(500, "EUR"), new Sku("Q-42"), null));

        assertThat(orders.findBySku(new Sku("Q-42"))).extracting(PurchaseOrder::getId).containsExactly(id);
        assertThat(orders.findByTotal(new Money(500, "EUR"))).extracting(PurchaseOrder::getId).containsExactly(id);
    }

    @Test
    void dirtyCheckingSeesAReplacedValue() {
        OrderId id = OrderId.random();
        orders.save(new PurchaseOrder(id, new Money(100, "USD"), new Sku("D-1"), null));

        tx.executeWithoutResult(s -> em.find(PurchaseOrder.class, id).setTotal(new Money(250, "USD")));

        assertThat(orders.findById(id).orElseThrow().getTotal()).isEqualTo(new Money(250, "USD"));
    }

    @Test
    void persistenceContextResolvesEqualIdsToTheSameEntity() {
        OrderId id = OrderId.random();
        orders.save(new PurchaseOrder(id, new Money(1, "USD"), new Sku("P-1"), null));

        tx.executeWithoutResult(s -> {
            PurchaseOrder a = em.find(PurchaseOrder.class, new OrderId(id.value()));
            PurchaseOrder b = em.find(PurchaseOrder.class, new OrderId(id.value()));
            assertThat(a).isSameAs(b);
        });
    }

    @Test
    void lazyProxyForAssociationStillWorks() {
        Customer customer = tx.execute(s -> {
            Customer c = new Customer("Grace");
            em.persist(c);
            return c;
        });
        OrderId id = OrderId.random();
        orders.save(new PurchaseOrder(id, new Money(1, "USD"), new Sku("L-1"), customer));

        tx.executeWithoutResult(s -> {
            PurchaseOrder loaded = em.find(PurchaseOrder.class, id);
            Customer lazy = loaded.getCustomer();
            System.out.printf("%nlazy association class: %s%n", lazy.getClass().getName());
            assertThat(lazy).isInstanceOf(HibernateProxy.class);
            assertThat(Hibernate.isInitialized(lazy)).isFalse();
            assertThat(lazy.getName()).isEqualTo("Grace");
        });
    }
}
