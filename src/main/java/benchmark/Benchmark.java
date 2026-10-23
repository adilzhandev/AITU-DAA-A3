
package benchmark;

import algorithms.*;
import graph.Graph;
import metrics.Metrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Assignment 3 reproducible benchmark.
 *
 * 5 graph sizes x 2 densities x 5 algorithms = 50 CSV rows.
 * Each algorithm is measured 5 times, without separate warm-up runs.
 * Graph generation, validation, counters comparison and CSV output are
 * outside the timed interval; the complete compute call is inside it.
 *
 * Run with --smoke to check only the first configuration.
 */
public final class Benchmark {

    private static final int RUNS = 5;

    private static final int[] SIZES = {
            1_000,
            3_162,
            10_000,
            31_623,
            100_000
    };

    private static final int[] DENSITIES = {4, 20};

    // Reference values from Assignment 3, page 4.
    // Order: sparse, dense for each graph size.
    private static final long[] EXPECTED_MST = {
            147_970L,
            30_363L,
            468_911L,
            97_589L,
            1_486_139L,
            309_550L,
            4_715_515L,
            968_517L,
            15_012_094L,
            3_056_681L
    };

    private static final long[] EXPECTED_SCC = {
            27L,
            1L,
            137L,
            1L,
            373L,
            1L,
            1_188L,
            1L,
            3_923L,
            1L
    };

    private static final long[] EXPECTED_DAG = {
            396_274L,
            252_411L,
            1_271_911L,
            723_157L,
            3_941_828L,
            2_381_342L,
            12_551_891L,
            7_376_612L,
            40_096_539L,
            23_512_087L
    };

    private record Snapshot(long value, Metrics metrics) {
    }

    private record Measurement(
            long value,
            Metrics metrics,
            double medianMs
    ) {
    }

    private Benchmark() {
    }

    public static void main(String[] args) throws IOException {

        boolean smoke = args.length == 1
                && args[0].equals("--smoke");

        if (args.length > 0 && !smoke) {
            throw new IllegalArgumentException(
                    "Usage: Benchmark [--smoke]"
            );
        }

        StringBuilder csv = new StringBuilder();

        csv.append(
                "algorithm,graph,n,m,time_ms," +
                        "dsu_ops,edge_scans,comparisons,max_depth,result\n"
        );

        int configurations = 0;
        int rows = 0;

        for (int i = 0; i < SIZES.length; i++) {

            int n = SIZES[i];

            for (int d = 0; d < DENSITIES.length; d++) {

                if (smoke && (i != 0 || d != 0)) {
                    continue;
                }

                int m = n * DENSITIES[d];

                String density = d == 0
                        ? "sparse"
                        : "dense";

                int referenceIndex = i * 2 + d;

                System.out.printf(
                        "%n=== n=%d, m=%d, %s ===%n",
                        n, m, density
                );

                // Release each graph before generating
                // the next graph kind.
                rows += benchmarkMST(
                        n, m, density,
                        EXPECTED_MST[referenceIndex],
                        csv
                );

                rows += benchmarkSCC(
                        n, m, density,
                        EXPECTED_SCC[referenceIndex],
                        csv
                );

                rows += benchmarkDAG(
                        n, m, density,
                        EXPECTED_DAG[referenceIndex],
                        csv
                );

                configurations++;
            }
        }

        if (smoke) {
            System.out.println(
                    "\nSMOKE TEST PASSED: " + rows +
                            " algorithm results checked."
            );
            System.out.println(
                    "No CSV written in smoke mode."
            );
            return;
        }

        if (configurations != 10 || rows != 50) {
            throw new AssertionError(
                    "Expected 10 configurations and 50 rows, got "
                            + configurations + " and " + rows
            );
        }

        Path output = Path.of(
                "results", "results.csv"
        );

        Files.createDirectories(output.getParent());

        Files.writeString(
                output,
                csv.toString(),
                StandardCharsets.UTF_8
        );

        System.out.println(
                "\nBENCHMARK PASSED: all 10 configurations."
        );

        System.out.println(
                "50 rows saved to: " +
                        output.toAbsolutePath()
        );
    }

    private static int benchmarkMST(
            int n,
            int m,
            String density,
            long expected,
            StringBuilder csv
    ) {
        Graph graph =
                GraphGenerator.generateUndirected(n, m);

        Measurement kruskal = measure(
                () -> Kruskal.compute(graph), result -> {

            check(
                    result.getComponentCount() == 1,
                    "Kruskal must return a connected MST"
            );

            check(
                    result.getEdgeCount() == n - 1,
                    "Kruskal edge count is wrong"
            );

            return new Snapshot(
                    result.getTotalWeight(),
                    result.getMetrics()
            );
        });

        Measurement prim = measure(
                () -> Prim.compute(graph), result -> {

            check(
                    result.getComponentCount() == 1,
                    "Prim must return a connected MST"
            );

            check(
                    result.getEdgeCount() == n - 1,
                    "Prim edge count is wrong"
            );

            return new Snapshot(
                    result.getTotalWeight(),
                    result.getMetrics()
            );
        });

        checkEqual(
                "Kruskal vs Prim",
                kruskal.value(),
                prim.value()
        );

        checkEqual(
                "MST reference",
                expected,
                kruskal.value()
        );

        appendRow(
                csv, "Kruskal",
                "undirected_" + density,
                n, m, kruskal
        );

        appendRow(
                csv, "Prim",
                "undirected_" + density,
                n, m, prim
        );

        printResult("Kruskal", kruskal);
        printResult("Prim", prim);

        return 2;
    }

    private static int benchmarkSCC(
            int n,
            int m,
            String density,
            long expected,
            StringBuilder csv
    ) {
        Graph graph =
                GraphGenerator.generateDirected(n, m);

        Measurement kosaraju = measure(
                () -> Kosaraju.compute(graph), result -> {

            checkEqual(
                    "Kosaraju edgeScans",
                    2L * m,
                    result.getMetrics().getEdgeScans()
            );

            return new Snapshot(
                    result.getComponentCount(),
                    result.getMetrics()
            );
        });

        checkEqual(
                "SCC reference",
                expected,
                kosaraju.value()
        );

        appendRow(
                csv, "Kosaraju",
                "directed_" + density,
                n, m, kosaraju
        );

        printResult("Kosaraju", kosaraju);

        return 1;
    }

    private static int benchmarkDAG(
            int n,
            int m,
            String density,
            long expected,
            StringBuilder csv
    ) {
        Graph graph =
                GraphGenerator.generateDAG(n, m);

        Measurement kahn = measure(
                () -> Kahn.compute(graph), result -> {

            check(
                    !result.hasCycle(),
                    "Generated DAG contains a cycle"
            );

            checkEqual(
                    "Kahn order size",
                    n,
                    result.getOrderSize()
            );

            Kahn.validateOrder(
                    graph, result.getOrder()
            );

            checkEqual(
                    "Kahn edgeScans",
                    m,
                    result.getMetrics().getEdgeScans()
            );

            return new Snapshot(
                    result.getOrderSize(),
                    result.getMetrics()
            );
        });

        Measurement shortest = measure(
                () -> DagShortestPath.compute(graph), result -> {

            checkEqual(
                    "DAG edgeScans",
                    m,
                    result.getMetrics().getEdgeScans()
            );

            return new Snapshot(
                    result.sumDistances(),
                    result.getMetrics()
            );
        });

        checkEqual(
                "DAG distances reference",
                expected,
                shortest.value()
        );

        appendRow(
                csv, "Kahn",
                "dag_" + density,
                n, m, kahn
        );

        appendRow(
                csv, "DagShortestPath",
                "dag_" + density,
                n, m, shortest
        );

        printResult("Kahn", kahn);
        printResult("DagShortestPath", shortest);

        return 2;
    }

    /**
     * Measures only the algorithm compute call, then validates every result.
     * Result aggregation (e.g. summing distances), validation and counter
     * comparisons must not inflate the measured algorithm time.
     */
    private static <T> Measurement measure(
            Supplier<T> algorithm,
            Function<T, Snapshot> validateAndSnapshot
    ) {
        long[] times = new long[RUNS];

        Snapshot first = null;

        for (int run = 0; run < RUNS; run++) {

            long start = System.nanoTime();

            T result = algorithm.get();

            long elapsed = System.nanoTime() - start;

            times[run] = elapsed;

            Snapshot current = validateAndSnapshot.apply(result);

            if (first == null) {
                first = current;
            } else {
                checkEqual(
                        "Repeated run result",
                        first.value(),
                        current.value()
                );

                compareCounters(
                        first.metrics(),
                        current.metrics()
                );
            }
        }

        Arrays.sort(times);

        double medianMs = times[RUNS / 2] / 1_000_000.0;

        return new Measurement(
                first.value(),
                first.metrics(),
                medianMs
        );
    }

    private static void compareCounters(
            Metrics a,
            Metrics b
    ) {
        checkEqual(
                "dsuOps",
                a.getDsuOps(),
                b.getDsuOps()
        );

        checkEqual(
                "edgeScans",
                a.getEdgeScans(),
                b.getEdgeScans()
        );

        checkEqual(
                "comparisons",
                a.getComparisons(),
                b.getComparisons()
        );

        checkEqual(
                "maxDepth",
                a.getMaxDepth(),
                b.getMaxDepth()
        );
    }

    private static void appendRow(
            StringBuilder csv,
            String algorithm,
            String graph,
            int n,
            int m,
            Measurement measurement
    ) {
        Metrics metrics = measurement.metrics();

        csv.append(String.format(
                Locale.US,
                "%s,%s,%d,%d,%.3f,%d,%d,%d,%d,%d%n",
                algorithm,
                graph,
                n,
                m,
                measurement.medianMs(),
                metrics.getDsuOps(),
                metrics.getEdgeScans(),
                metrics.getComparisons(),
                metrics.getMaxDepth(),
                measurement.value()
        ));
    }

    private static void printResult(
            String algorithm,
            Measurement result
    ) {
        System.out.printf(
                Locale.US,
                "%-18s result=%-12d median=%.3f ms%n",
                algorithm,
                result.value(),
                result.medianMs()
        );
    }

    private static void checkEqual(
            String label,
            long expected,
            long actual
    ) {
        if (expected != actual) {
            throw new AssertionError(
                    label + ": expected=" +
                            expected + ", actual=" + actual
            );
        }
    }

    private static void check(
            boolean condition,
            String message
    ) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
