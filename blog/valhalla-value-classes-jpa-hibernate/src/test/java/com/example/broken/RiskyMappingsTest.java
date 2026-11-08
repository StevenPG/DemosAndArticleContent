package com.example.broken;

import java.util.function.Consumer;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Mappings that might not survive contact with value classes. Each scenario
 * gets its own SessionFactory so one failure can't hide another. The test
 * prints exactly what happened (success, or the root-cause exception).
 */
class RiskyMappingsTest {

    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @BeforeAll
    static void start() {
        postgres.start();
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    static SessionFactory sessionFactory(Class<?>... entities) {
        Configuration cfg = new Configuration()
                .setProperty("hibernate.connection.url", postgres.getJdbcUrl())
                .setProperty("hibernate.connection.username", postgres.getUsername())
                .setProperty("hibernate.connection.password", postgres.getPassword())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        for (Class<?> entity : entities) {
            cfg.addAnnotatedClass(entity);
        }
        return cfg.buildSessionFactory();
    }

    static String probe(String scenario, Class<?>[] entities, Consumer<SessionFactory> body) {
        String outcome;
        try (SessionFactory sf = sessionFactory(entities)) {
            body.accept(sf);
            outcome = "WORKS";
        } catch (Throwable t) {
            Throwable root = t;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            java.util.Arrays.stream(t.getStackTrace())
                    .filter(f -> f.getClassName().startsWith("org.hibernate") || f.getClassName().startsWith("com.example"))
                    .limit(12)
                    .forEach(f -> System.out.println("        at " + f));
            outcome = "FAILS  " + t.getClass().getName() + ": " + t.getMessage()
                    + (root != t ? "%n        root cause: %s: %s".formatted(root.getClass().getName(), root.getMessage()) : "");
        }
        System.out.printf("%n=== SCENARIO: %s ===%n%s%n", scenario, outcome);
        return outcome;
    }

    @Test
    void valueClassAsEntity() {
        probe("value class as @Entity", new Class<?>[] {ValuePointEntity.class}, sf -> {
            sf.inTransaction(s -> s.persist(new ValuePointEntity(1L, 3, 4)));
            System.out.println("persist + commit: ok");
            ValuePointEntity loaded = sf.fromTransaction(s -> s.find(ValuePointEntity.class, 1L));
            System.out.println("loaded x=" + loaded.x());
        });
    }

    @Test
    void nonRecordValueEmbeddableWithNoArgConstructor() {
        probe("non-record value class @Embeddable (no-arg ctor)", new Class<?>[] {Parcel.class}, sf -> {
            sf.inTransaction(s -> s.persist(new Parcel(1L, new Dimensions(200, 300))));
            System.out.println("persist + commit: ok");
            Parcel loaded = sf.fromTransaction(s -> s.find(Parcel.class, 1L));
            System.out.println("loaded dims=" + loaded.getDims().widthMm() + "x" + loaded.getDims().heightMm());
            if (loaded.getDims().widthMm() != 200) {
                throw new AssertionError("dimensions not hydrated: width=" + loaded.getDims().widthMm());
            }
        });
    }

    @Test
    void nonRecordValueEmbeddableWithInstantiator() {
        probe("non-record value class @Embeddable (@Instantiator)", new Class<?>[] {Crate.class}, sf -> {
            sf.inTransaction(s -> s.persist(new Crate(1L, new DimensionsWithInstantiator(200, 300))));
            Crate loaded = sf.fromTransaction(s -> s.find(Crate.class, 1L));
            System.out.println("loaded dims=" + loaded.getDims().widthMm() + "x" + loaded.getDims().heightMm());
            if (loaded.getDims().widthMm() != 200) {
                throw new AssertionError("dimensions not hydrated: width=" + loaded.getDims().widthMm());
            }
        });
    }

    @Test
    void valueRecordAsIdClass() {
        probe("value record as @IdClass", new Class<?>[] {OrderLine.class}, sf -> {
            sf.inTransaction(s -> s.persist(new OrderLine(10L, 1, "first")));
            OrderLine loaded = sf.fromTransaction(s -> s.find(OrderLine.class, new LineKey(10L, 1)));
            System.out.println("loaded note=" + (loaded == null ? null : loaded.getNote()));
            if (loaded == null) {
                throw new AssertionError("find by LineKey returned null");
            }
        });
    }
}
