package benchmark;

import algorithms.Condensation;
import algorithms.CondensationResult;
import algorithms.DagLongestPath;
import algorithms.DagLongestPathResult;
import algorithms.Kosaraju;
import algorithms.SCCResult;
import algorithms.Tarjan;
import graph.Edge;
import graph.Graph;
import metrics.Metrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Optional bonuses on the same ten directed configurations as Benchmark.
 * Each complete compute call is timed five times, with no separate warm-up;
 * generation, independent validation, aggregation and I/O are outside timing.
 * Condensation time includes Tarjan, but its scan counter counts only the
 * contraction phase. Longest-path time includes topological sorting, while
 * its scan counter counts only relaxation, matching DagShortestPath.
 * The mandatory results/results.csv is never changed by this runner.
 */
public final class BonusBenchmark {

    private static final int RUNS = 5;
    private static final int[] SIZES = {1_000, 3_162, 10_000, 31_623, 100_000};
    private static final int[] DENSITIES = {4, 20};
    private static final int[] EXPECTED_SCC = {27, 1, 137, 1, 373, 1, 1_188, 1, 3_923, 1};

    private record Snapshot(long value, Metrics metrics) {
    }

    private record Measurement<T>(T result, Snapshot snapshot, double medianMs) {
    }

    private BonusBenchmark() {
    }

    public static void main(String[] args) throws IOException {
        boolean smoke = args.length == 1 && args[0].equals("--smoke");
        boolean demo = args.length == 1 && args[0].equals("--demo");
        if (args.length > 0 && !smoke && !demo) {
            throw new IllegalArgumentException("Usage: BonusBenchmark [--smoke|--demo]");
        }
        if (demo) {
            runDemo();
            return;
        }

        StringBuilder csv = new StringBuilder(
                "algorithm,graph,n,m,time_ms,dsu_ops,edge_scans,comparisons,max_depth,result\n"
        );
        int configurations = 0;
        int rows = 0;
        for (int i = 0; i < SIZES.length; i++) {
            for (int d = 0; d < DENSITIES.length; d++) {
                if (smoke && (i != 0 || d != 0)) {
                    continue;
                }
                int n = SIZES[i];
                int m = n * DENSITIES[d];
                String density = d == 0 ? "sparse" : "dense";
                System.out.printf("%n=== BONUS n=%d, m=%d, %s ===%n", n, m, density);
                rows += benchmark(n, m, density, EXPECTED_SCC[i * 2 + d], csv);
                configurations++;
            }
        }

        // This small graph demonstrates a nontrivial path even when a dense
        // random graph contracts to a single SCC with no remaining edges.
        runDemo();
        if (smoke) {
            System.out.println("\nBONUS SMOKE PASSED: " + rows + " algorithm results checked.");
            System.out.println("No CSV written in smoke mode.");
            return;
        }
        checkEqual("Configuration count", 10, configurations);
        checkEqual("CSV row count", 40, rows);
        Path output = Path.of("results", "bonus.csv");
        Files.createDirectories(output.getParent());
        Files.writeString(output, csv.toString(), StandardCharsets.UTF_8);
        System.out.println("\nBONUS BENCHMARK PASSED: all 10 configurations and scheduler demo.");
        System.out.println("40 rows saved to: " + output.toAbsolutePath());
    }

    private static int benchmark(int n, int m, String density,
                                 int expectedScc, StringBuilder csv) {
        Graph graph = GraphGenerator.generateDirected(n, m);
        Measurement<SCCResult> kosaraju = measure(() -> Kosaraju.compute(graph), result -> {
            checkEqual("Kosaraju reference SCC count", expectedScc, result.getComponentCount());
            checkEqual("Kosaraju edgeScans", 2L * m, result.getMetrics().getEdgeScans());
            return new Snapshot(result.getComponentCount(), result.getMetrics());
        });
        Measurement<SCCResult> tarjan = measure(() -> Tarjan.compute(graph), result -> {
            validatePartitions(kosaraju.result(), result);
            checkEqual("Tarjan edgeScans", m, result.getMetrics().getEdgeScans());
            return new Snapshot(result.getComponentCount(), result.getMetrics());
        });
        Measurement<CondensationResult> condensation = measure(
                () -> Condensation.compute(graph), result -> {
                    validatePartitions(kosaraju.result(), result.getComponents());
                    validateCondensation(graph, result);
                    checkEqual("Condensation edgeScans", m, result.getMetrics().getEdgeScans());
                    return new Snapshot(result.getGraph().edgeCount(), result.getMetrics());
                }
        );
        Graph dag = condensation.result().getGraph();
        Measurement<DagLongestPathResult> longest = measure(
                () -> DagLongestPath.compute(dag), result -> {
                    validateLongestPaths(dag, result);
                    checkEqual("DagLongestPath edgeScans", dag.edgeCount(),
                            result.getMetrics().getEdgeScans());
                    return new Snapshot(result.getCriticalPathWeight(), result.getMetrics());
                }
        );

        appendRow(csv, "Kosaraju", "directed_" + density, n, m, kosaraju);
        appendRow(csv, "Tarjan", "directed_" + density, n, m, tarjan);
        appendRow(csv, "Condensation", "directed_" + density, n, m, condensation);
        appendRow(csv, "DagLongestPath", "condensation_" + density + "_" + n,
                dag.vertexCount(), dag.edgeCount(), longest);
        printResult("Kosaraju", kosaraju);
        printResult("Tarjan", tarjan);
        printResult("Condensation", condensation);
        printResult("DagLongestPath", longest);
        System.out.printf(Locale.US,
                "Full SCC partitions match; Tarjan/Kosaraju median time = %.3f.%n",
                tarjan.medianMs() / kosaraju.medianMs());
        System.out.printf("Condensation: %d vertices, %d edges; critical path %s, weight %d.%n",
                dag.vertexCount(), dag.edgeCount(), longest.result().getCriticalPath(),
                longest.result().getCriticalPathWeight());
        return 4;
    }

    /** Checks a bijection between component labels in O(V), independent of IDs. */
    static void validatePartitions(SCCResult expected, SCCResult actual) {
        checkEqual("SCC component count", expected.getComponentCount(), actual.getComponentCount());
        int[] expectedIds = expected.getComponentIds();
        int[] actualIds = actual.getComponentIds();
        checkEqual("SCC vertex count", expectedIds.length, actualIds.length);
        int count = expected.getComponentCount();
        int[] forward = new int[count];
        int[] backward = new int[count];
        Arrays.fill(forward, -1);
        Arrays.fill(backward, -1);
        int seen = 0;
        for (int vertex = 0; vertex < expectedIds.length; vertex++) {
            int a = expectedIds[vertex];
            int b = actualIds[vertex];
            check(a >= 0 && a < count && b >= 0 && b < count, "Invalid SCC label");
            if (forward[a] == -1) {
                check(backward[b] == -1, "SCC partitions merge different components");
                forward[a] = b;
                backward[b] = a;
                seen++;
            }
            check(forward[a] == b && backward[b] == a,
                    "SCC partitions differ at vertex " + vertex);
        }
        checkEqual("Nonempty SCC count", count, seen);
    }

    /** Independently checks every contracted pair and its maximum original weight. */
    static void validateCondensation(Graph original, CondensationResult result) {
        Graph dag = result.getGraph();
        SCCResult components = result.getComponents();
        check(dag.isDirected(), "Condensation must be directed");
        checkEqual("Condensation vertex count", components.getComponentCount(), dag.vertexCount());
        checkEqual("Condensation mapping size", original.vertexCount(),
                components.getComponentIds().length);
        Map<Long, Integer> expectedWeights = new HashMap<>();
        for (Edge edge : original.edges()) {
            int from = components.getComponentId(edge.from);
            int to = components.getComponentId(edge.to);
            if (from != to) {
                expectedWeights.merge(pairKey(from, to), edge.weight, Math::max);
            }
        }
        checkEqual("Condensation edge count", expectedWeights.size(), dag.edgeCount());
        for (Edge edge : dag.edges()) {
            check(edge.from != edge.to, "Condensation contains a self-loop");
            Integer expected = expectedWeights.remove(pairKey(edge.from, edge.to));
            check(expected != null, "Unexpected or duplicate condensation edge");
            checkEqual("Condensation edge weight", expected, edge.weight);
        }
        check(expectedWeights.isEmpty(), "Missing condensation edges");
        independentTopologicalOrder(dag);
    }

    /** Validates the recurrence and a real source-to-sink witness outside timing. */
    static void validateLongestPaths(Graph dag, DagLongestPathResult result) {
        int n = dag.vertexCount();
        checkEqual("Longest-path distance count", n, result.getDistances().length);
        int[] order = independentTopologicalOrder(dag);
        int[] indegree = new int[n];
        for (Edge edge : dag.edges()) {
            indegree[edge.to]++;
        }
        long[] expected = new long[n];
        Arrays.fill(expected, Long.MIN_VALUE);
        for (int vertex = 0; vertex < n; vertex++) {
            if (indegree[vertex] == 0) {
                expected[vertex] = 0;
            }
        }
        long expectedCritical = n == 0 ? 0 : Long.MIN_VALUE;
        for (int vertex : order) {
            check(expected[vertex] != Long.MIN_VALUE, "DAG vertex has no source ancestor");
            for (Edge edge : dag.neighbors(vertex)) {
                expected[edge.to] = Math.max(expected[edge.to], expected[vertex] + edge.weight);
            }
            if (dag.neighbors(vertex).isEmpty()) {
                expectedCritical = Math.max(expectedCritical, expected[vertex]);
            }
        }
        for (int vertex = 0; vertex < n; vertex++) {
            checkEqual("Longest distance to " + vertex, expected[vertex], result.distanceTo(vertex));
        }
        checkEqual("Critical-path weight", expectedCritical, result.getCriticalPathWeight());
        List<Integer> path = result.getCriticalPath();
        if (n == 0) {
            check(path.isEmpty(), "Empty DAG has a nonempty critical path");
            return;
        }
        check(!path.isEmpty() && path.size() <= n, "Invalid critical-path length");
        boolean[] visited = new boolean[n];
        for (int vertex : path) {
            check(vertex >= 0 && vertex < n && !visited[vertex], "Invalid critical-path vertex");
            visited[vertex] = true;
        }
        check(indegree[path.get(0)] == 0, "Critical path must start at a source");
        check(dag.neighbors(path.get(path.size() - 1)).isEmpty(),
                "Critical path must end at a sink");
        long weight = 0;
        for (int i = 1; i < path.size(); i++) {
            Integer bestWeight = null;
            for (Edge edge : dag.neighbors(path.get(i - 1))) {
                if (edge.to == path.get(i)) {
                    bestWeight = bestWeight == null ? edge.weight : Math.max(bestWeight, edge.weight);
                }
            }
            check(bestWeight != null, "Critical path uses a missing edge");
            weight += bestWeight;
        }
        checkEqual("Critical-path witness weight", expectedCritical, weight);
    }

    /** Local Kahn implementation, independent of the production algorithms.Kahn. */
    private static int[] independentTopologicalOrder(Graph graph) {
        int n = graph.vertexCount();
        int[] indegree = new int[n];
        for (Edge edge : graph.edges()) {
            indegree[edge.to]++;
        }
        int[] order = new int[n];
        int head = 0;
        int tail = 0;
        for (int vertex = 0; vertex < n; vertex++) {
            if (indegree[vertex] == 0) {
                order[tail++] = vertex;
            }
        }
        while (head < tail) {
            for (Edge edge : graph.neighbors(order[head++])) {
                if (--indegree[edge.to] == 0) {
                    order[tail++] = edge.to;
                }
            }
        }
        checkEqual("Topological order size (cycle check)", n, tail);
        return order;
    }

    private static long pairKey(int from, int to) {
        return ((long) from << 32) | (to & 0xffffffffL);
    }

    private static void runDemo() throws IOException {
        Graph graph = Graph.fromFile(Path.of("data", "bonus-scheduler.txt"));
        CondensationResult condensation = Condensation.compute(graph);
        validatePartitions(Kosaraju.compute(graph), condensation.getComponents());
        validateCondensation(graph, condensation);
        DagLongestPathResult longest = DagLongestPath.compute(condensation.getGraph());
        validateLongestPaths(condensation.getGraph(), longest);
        checkEqual("Scheduler fixture SCC count", 5, condensation.getGraph().vertexCount());
        checkEqual("Scheduler fixture edge count", 5, condensation.getGraph().edgeCount());
        checkEqual("Scheduler fixture critical weight", 20, longest.getCriticalPathWeight());

        System.out.println("\n=== Scheduler demo: data/bonus-scheduler.txt ===");
        List<List<Integer>> members = new ArrayList<>();
        for (int i = 0; i < condensation.getGraph().vertexCount(); i++) {
            members.add(new ArrayList<>());
        }
        for (int vertex = 0; vertex < graph.vertexCount(); vertex++) {
            members.get(condensation.getComponents().getComponentId(vertex)).add(vertex);
        }
        for (int i = 0; i < members.size(); i++) {
            System.out.printf("Component %d = %s%n", i, members.get(i));
        }
        System.out.println("Condensation edges (maximum weight for each SCC pair):");
        for (Edge edge : condensation.getGraph().edges()) {
            System.out.println("  " + edge);
        }
        System.out.println("Critical component path: " + longest.getCriticalPath());
        List<List<Integer>> originalGroups = new ArrayList<>();
        for (int component : longest.getCriticalPath()) {
            originalGroups.add(members.get(component));
        }
        System.out.println("Original vertex groups: " + originalGroups);
        System.out.println("Critical path weight: " + longest.getCriticalPathWeight() + " = 7 + 5 + 8");
        System.out.println("Weights describe inter-component transitions; internal SCC edges are excluded.");
    }

    private static <T> Measurement<T> measure(Supplier<T> compute, Function<T, Snapshot> validate) {
        long[] times = new long[RUNS];
        T firstResult = null;
        Snapshot first = null;
        for (int run = 0; run < RUNS; run++) {
            long start = System.nanoTime();
            T result = compute.get();
            times[run] = System.nanoTime() - start;
            Snapshot current = validate.apply(result);
            if (first == null) {
                first = current;
                firstResult = result;
            } else {
                checkEqual("Repeated result", first.value(), current.value());
                compareCounters(first.metrics(), current.metrics());
            }
        }
        Arrays.sort(times);
        return new Measurement<>(firstResult, first, times[RUNS / 2] / 1_000_000.0);
    }

    private static void compareCounters(Metrics first, Metrics next) {
        checkEqual("Repeated dsuOps", first.getDsuOps(), next.getDsuOps());
        checkEqual("Repeated edgeScans", first.getEdgeScans(), next.getEdgeScans());
        checkEqual("Repeated comparisons", first.getComparisons(), next.getComparisons());
        checkEqual("Repeated maxDepth", first.getMaxDepth(), next.getMaxDepth());
    }

    private static void appendRow(StringBuilder csv, String algorithm, String graph,
                                  int n, int m, Measurement<?> measurement) {
        Metrics metrics = measurement.snapshot().metrics();
        csv.append(String.format(Locale.US, "%s,%s,%d,%d,%.3f,%d,%d,%d,%d,%d%n",
                algorithm, graph, n, m, measurement.medianMs(), metrics.getDsuOps(),
                metrics.getEdgeScans(), metrics.getComparisons(), metrics.getMaxDepth(),
                measurement.snapshot().value()));
    }

    private static void printResult(String algorithm, Measurement<?> result) {
        System.out.printf(Locale.US, "%-18s result=%-8d median=%.3f ms scans=%d depth=%d%n",
                algorithm, result.snapshot().value(), result.medianMs(),
                result.snapshot().metrics().getEdgeScans(), result.snapshot().metrics().getMaxDepth());
    }

    private static void checkEqual(String label, long expected, long actual) {
        check(expected == actual, label + ": expected=" + expected + ", actual=" + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
