package com.stevenpg.aot.warmup;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A reproduction of the benchmark in JEP 544 itself: compile 50 source files with javac,
 * in-process, once per iteration. javac is a big, branchy, allocation-heavy Java program
 * - one of the hardest realistic warmup cases the JDK team could pick.
 *
 * The 50 files are generated deterministically (records, sealed hierarchies, generics,
 * lambdas, switch patterns, cross-file references) so the benchmark has no external inputs.
 */
final class JavacWorkload implements WarmupMain.Workload {

    private static final int FILES = 50;

    private final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    private final List<Path> sources = new ArrayList<>();
    private final Path out;

    JavacWorkload() throws IOException {
        Path src = Files.createTempDirectory("jep544-src");
        out = Files.createTempDirectory("jep544-out");
        for (int i = 0; i < FILES; i++) {
            Path file = src.resolve("gen/Unit" + i + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source(i));
            sources.add(file);
        }
    }

    @Override
    public long runOnce() throws IOException {
        try (StandardJavaFileManager fm = javac.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(sources);
            boolean ok = javac.getTask(null, fm, null, List.of("-d", out.toString(), "-proc:none", "-g", "-Xlint:all"),
                    null, units).call();
            if (!ok) {
                throw new IllegalStateException("generated sources failed to compile");
            }
        }
        return Files.size(out.resolve("gen/Unit" + (FILES - 1) + ".class"));
    }

    private static String source(int i) {
        String prev = i == 0 ? "Object" : "Unit" + (i - 1) + ".Shape";
        return """
                package gen;

                import java.util.*;
                import java.util.function.*;
                import java.util.stream.*;

                public final class Unit%1$d {

                    public sealed interface Shape permits Circle, Rect, Poly {}
                    public record Circle(double r) implements Shape {}
                    public record Rect(double w, double h) implements Shape {}
                    public record Poly(List<double[]> pts) implements Shape {}

                    static <T extends Comparable<? super T>> Optional<T> maxOf(Collection<? extends T> xs) {
                        return xs.stream().max(Comparator.naturalOrder()).map(Function.identity());
                    }

                    static double area(Shape s) {
                        return switch (s) {
                            case Circle c when c.r() <= 0 -> 0;
                            case Circle c -> Math.PI * c.r() * c.r();
                            case Rect(double w, double h) -> w * h;
                            case Poly p -> {
                                double a = 0;
                                for (int i = 0; i < p.pts().size(); i++) {
                                    double[] u = p.pts().get(i), v = p.pts().get((i + 1) %% p.pts().size());
                                    a += u[0] * v[1] - v[0] * u[1];
                                }
                                yield Math.abs(a) / 2;
                            }
                        };
                    }

                    static Map<String, DoubleSummaryStatistics> summarize(List<? extends Shape> shapes, %2$s link) {
                        var tag = String.valueOf(link);
                        return shapes.stream().collect(Collectors.groupingBy(
                                s -> tag.isEmpty() ? "" : s.getClass().getSimpleName(), TreeMap::new,
                                Collectors.summarizingDouble(Unit%1$d::area)));
                    }

                    static String render(Map<String, DoubleSummaryStatistics> stats) {
                        var sb = new StringBuilder();
                        stats.forEach((k, v) -> sb.append("%%s: n=%%d avg=%%.3f%%n".formatted(k, v.getCount(), v.getAverage())));
                        BiFunction<String, Integer, String> pad = (s, n) -> s + " ".repeat(Math.max(0, n - s.length()));
                        return pad.apply(sb.toString(), %1$d);
                    }

                    public static void main(String[] args) {
                        List<Shape> shapes = IntStream.range(0, 100).mapToObj(i -> switch (i %% 3) {
                            case 0 -> (Shape) new Circle(i);
                            case 1 -> new Rect(i, i + 1);
                            default -> new Poly(List.of(new double[]{0, 0}, new double[]{i, 0}, new double[]{0, i}));
                        }).toList();
                        System.out.println(render(summarize(shapes, null)) + maxOf(List.of(3, 1, %1$d)));
                    }
                }
                """.formatted(i, prev);
    }
}
