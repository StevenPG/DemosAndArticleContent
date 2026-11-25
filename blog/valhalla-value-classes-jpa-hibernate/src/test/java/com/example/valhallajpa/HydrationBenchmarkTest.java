package com.example.valhallajpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Same table shape, same rows, two mappings: value-record embeddables
 * (ValueLine) versus identity-record embeddables (IdentityLine).
 *
 * <p>Measures, per full-table load through the EntityManager: wall time and
 * bytes allocated on the loading thread. Then measures a pure in-memory pass
 * over the loaded entities (where JVM field flattening could matter).
 *
 * <p>This is a "realistic app" measurement, not JMH. Rounds alternate between
 * the two mappings so JIT warm-up and GC noise hit both sides.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Tag("benchmark")
class HydrationBenchmarkTest {

    static final int ROWS = Integer.getInteger("bench.rows", 200_000);
    static final int WARMUP_ROUNDS = Integer.getInteger("bench.warmup", 10);
    static final int ROUNDS = Integer.getInteger("bench.rounds", 20);
    static final int COMPUTE_PASSES = 200;

    static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    @Autowired
    EntityManagerFactory emf;

    @Test
    void loadValueVsIdentityEmbeddables() {
        seed();

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            load(ValueLine.class);
            load(IdentityLine.class);
        }

        long[] valueNs = new long[ROUNDS], identityNs = new long[ROUNDS];
        long[] valueBytes = new long[ROUNDS], identityBytes = new long[ROUNDS];
        for (int i = 0; i < ROUNDS; i++) {
            // Alternate which side goes first each round.
            if (i % 2 == 0) {
                long[] v = load(ValueLine.class);
                long[] id = load(IdentityLine.class);
                valueNs[i] = v[0]; valueBytes[i] = v[1];
                identityNs[i] = id[0]; identityBytes[i] = id[1];
            } else {
                long[] id = load(IdentityLine.class);
                long[] v = load(ValueLine.class);
                valueNs[i] = v[0]; valueBytes[i] = v[1];
                identityNs[i] = id[0]; identityBytes[i] = id[1];
            }
        }

        System.out.printf("%n=== HYDRATION: %,d rows, %d warmup + %d measured rounds ===%n", ROWS, WARMUP_ROUNDS, ROUNDS);
        System.out.printf("Runtime: %s, Hibernate %s, heap max %d MB, JVM flags %s%n",
                Runtime.version(), org.hibernate.Version.getVersionString(), Runtime.getRuntime().maxMemory() >> 20,
                ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                        .filter(a -> a.startsWith("-XX") || a.startsWith("--enable")).toList());
        report("value records   ", valueNs, valueBytes);
        report("identity records", identityNs, identityBytes);

        // In-memory pass over already-loaded entities.
        List<ValueLine> values = loadDetached(ValueLine.class);
        List<IdentityLine> identities = loadDetached(IdentityLine.class);
        for (int i = 0; i < 5; i++) {
            sumValues(values);
            sumIdentities(identities);
        }
        long[] vCompute = new long[ROUNDS], iCompute = new long[ROUNDS];
        long[] vComputeBytes = new long[ROUNDS], iComputeBytes = new long[ROUNDS];
        long tid = Thread.currentThread().threadId();
        long check = 0;
        for (int i = 0; i < ROUNDS; i++) {
            for (int side = 0; side < 2; side++) {
                boolean valueSide = (i + side) % 2 == 0; // alternate who goes first
                long b0 = THREADS.getThreadAllocatedBytes(tid);
                long t0 = System.nanoTime();
                check += valueSide ? sumValues(values) : sumIdentities(identities);
                long t1 = System.nanoTime();
                long b1 = THREADS.getThreadAllocatedBytes(tid);
                if (valueSide) {
                    vCompute[i] = t1 - t0; vComputeBytes[i] = b1 - b0;
                } else {
                    iCompute[i] = t1 - t0; iComputeBytes[i] = b1 - b0;
                }
            }
        }
        System.out.printf("%n=== IN-MEMORY: %d passes over %,d loaded entities (checksum %d) ===%n", COMPUTE_PASSES, ROWS, check);
        System.out.printf("value records    median %8.2f ms  min %8.2f ms  |  allocated median %8.1f MB%n",
                median(vCompute) / 1e6, min(vCompute) / 1e6, median(vComputeBytes) / 1048576.0);
        System.out.printf("identity records median %8.2f ms  min %8.2f ms  |  allocated median %8.1f MB%n",
                median(iCompute) / 1e6, min(iCompute) / 1e6, median(iComputeBytes) / 1048576.0);
    }

    void seed() {
        SessionFactory sf = emf.unwrap(SessionFactory.class);
        try (StatelessSession s = sf.openStatelessSession()) {
            s.setJdbcBatchSize(500);
            var tx = s.beginTransaction();
            for (int i = 1; i <= ROWS; i++) {
                long cents = 100 + (i % 9_900);
                String cur = (i % 3 == 0) ? "EUR" : "USD";
                int units = 1 + (i % 50);
                s.insert(new ValueLine((long) i, new Money(cents, cur), new Quantity(units)));
                s.insert(new IdentityLine((long) i, new IdentityMoney(cents, cur), new IdentityQuantity(units)));
            }
            tx.commit();
        }
    }

    /** Returns {elapsedNanos, bytesAllocatedOnThisThread}. */
    long[] load(Class<?> type) {
        long tid = Thread.currentThread().threadId();
        long b0 = THREADS.getThreadAllocatedBytes(tid);
        long t0 = System.nanoTime();
        long sum = 0;
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            List<?> rows = em.createQuery("from " + type.getSimpleName(), type)
                    .setHint("org.hibernate.readOnly", true)
                    .getResultList();
            if (rows.size() != ROWS) {
                throw new IllegalStateException("expected " + ROWS + " rows, got " + rows.size());
            }
            for (Object row : rows) {
                sum += row instanceof ValueLine v ? v.lineTotalCents() : ((IdentityLine) row).lineTotalCents();
            }
            em.getTransaction().commit();
        }
        long t1 = System.nanoTime();
        long b1 = THREADS.getThreadAllocatedBytes(tid);
        if (sum == 42) {
            System.out.println("unlikely");
        }
        return new long[] {t1 - t0, b1 - b0};
    }

    <T> List<T> loadDetached(Class<T> type) {
        try (EntityManager em = emf.createEntityManager()) {
            return new ArrayList<>(em.createQuery("from " + type.getSimpleName(), type).getResultList());
        }
    }

    static long sumValues(List<ValueLine> lines) {
        long sum = 0;
        for (int p = 0; p < COMPUTE_PASSES; p++) {
            for (ValueLine l : lines) {
                sum += l.lineTotalCents();
            }
        }
        return sum;
    }

    static long sumIdentities(List<IdentityLine> lines) {
        long sum = 0;
        for (int p = 0; p < COMPUTE_PASSES; p++) {
            for (IdentityLine l : lines) {
                sum += l.lineTotalCents();
            }
        }
        return sum;
    }

    static void report(String label, long[] ns, long[] bytes) {
        System.out.printf("%s  median %8.2f ms  min %8.2f ms  |  allocated median %7.1f MB  (%5.0f B/row)%n",
                label, median(ns) / 1e6, min(ns) / 1e6, median(bytes) / 1048576.0, (double) median(bytes) / ROWS);
    }

    static double median(long[] xs) {
        long[] s = xs.clone();
        Arrays.sort(s);
        return s.length % 2 == 1 ? s[s.length / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2.0;
    }

    static long min(long[] xs) {
        return Arrays.stream(xs).min().orElseThrow();
    }
}
