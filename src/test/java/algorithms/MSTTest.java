
package algorithms;

import graph.Graph;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MSTTest {

    @Test
    void simpleConnectedGraph() {
        Graph graph = new Graph(4, false);

        graph.addEdge(0, 1, 10);
        graph.addEdge(0, 2, 6);
        graph.addEdge(0, 3, 5);
        graph.addEdge(1, 3, 15);

        MSTResult kruskal = Kruskal.compute(graph);
        MSTResult prim = Prim.compute(graph);

        assertEquals(21, kruskal.getTotalWeight());
        assertEquals(21, prim.getTotalWeight());

        assertEquals(3, kruskal.getEdgeCount());
        assertEquals(3, prim.getEdgeCount());

        assertEquals(1, kruskal.getComponentCount());
        assertEquals(1, prim.getComponentCount());
    }

    @Test
    void disconnectedGraphReturnsForest() {
        Graph graph = new Graph(5, false);

        graph.addEdge(0, 1, 3);
        graph.addEdge(2, 3, 7);

        MSTResult kruskal = Kruskal.compute(graph);
        MSTResult prim = Prim.compute(graph);

        assertEquals(10, kruskal.getTotalWeight());
        assertEquals(10, prim.getTotalWeight());

        assertEquals(3, kruskal.getComponentCount());
        assertEquals(3, prim.getComponentCount());

        assertEquals(2, kruskal.getEdgeCount());
        assertEquals(2, prim.getEdgeCount());
    }

    @Test
    void handlesParallelEdgesAndSelfLoops() {
        Graph graph = new Graph(3, false);

        graph.addEdge(0, 0, -100);
        graph.addEdge(0, 1, 4);
        graph.addEdge(0, 1, 2);
        graph.addEdge(1, 2, 3);

        MSTResult kruskal = Kruskal.compute(graph);
        MSTResult prim = Prim.compute(graph);

        assertEquals(5, kruskal.getTotalWeight());
        assertEquals(5, prim.getTotalWeight());

        assertEquals(2, kruskal.getEdgeCount());
        assertEquals(2, prim.getEdgeCount());
    }

    @Test
    void emptyGraphIsSupported() {
        Graph graph = new Graph(0, false);

        MSTResult kruskal = Kruskal.compute(graph);
        MSTResult prim = Prim.compute(graph);

        assertEquals(0, kruskal.getTotalWeight());
        assertEquals(0, prim.getTotalWeight());

        assertEquals(0, kruskal.getComponentCount());
        assertEquals(0, prim.getComponentCount());
    }

    @Test
    void directedGraphsAreRejected() {
        Graph graph = new Graph(3, true);
        graph.addEdge(0, 1, 5);

        assertThrows(
                IllegalArgumentException.class,
                () -> Kruskal.compute(graph)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Prim.compute(graph)
        );
    }

    @Test
    void primCountsAllUndirectedAdjacencyScans() {
        Graph graph = new Graph(4, false);

        graph.addEdge(0, 1, 5);
        graph.addEdge(1, 2, 7);
        graph.addEdge(2, 3, 3);

        MSTResult result = Prim.compute(graph);

        assertEquals(
                2L * graph.edgeCount(),
                result.getMetrics().getEdgeScans()
        );
    }

    @Test
    void hundredRandomConnectedGraphsAgree() {
        Random random = new Random(42);

        for (int test = 0; test < 100; test++) {

            int n = 2 + random.nextInt(40);

            Graph graph = new Graph(n, false);

            // Random spanning tree ensures connectivity.
            for (int v = 1; v < n; v++) {
                int u = random.nextInt(v);
                int weight = random.nextInt(201) - 100;

                graph.addEdge(u, v, weight);
            }

            // Additional random edges.
            int extra = random.nextInt(n * 3 + 1);

            for (int i = 0; i < extra; i++) {
                int u = random.nextInt(n);
                int v = random.nextInt(n);
                int weight = random.nextInt(201) - 100;

                graph.addEdge(u, v, weight);
            }

            MSTResult kruskal = Kruskal.compute(graph);
            MSTResult prim = Prim.compute(graph);

            assertEquals(
                    kruskal.getTotalWeight(),
                    prim.getTotalWeight(),
                    "MST mismatch in random graph " + test
            );

            assertEquals(n - 1, kruskal.getEdgeCount());
            assertEquals(n - 1, prim.getEdgeCount());

            assertEquals(1, kruskal.getComponentCount());
            assertEquals(1, prim.getComponentCount());
        }
    }

    @Test
    void smallRandomForestsMatchExhaustiveSearch() {
        Random random = new Random(91);

        for (int test = 0; test < 80; test++) {
            int n = 1 + random.nextInt(6);
            Graph graph = new Graph(n, false);
            int m = random.nextInt(11);
            for (int i = 0; i < m; i++) {
                graph.addEdge(random.nextInt(n), random.nextInt(n),
                        random.nextInt(21) - 10);
            }

            int[] originalLabels = singletonLabels(n);
            for (var edge : graph.edges()) {
                mergeLabels(originalLabels, edge.from, edge.to);
            }
            int components = (int) Arrays.stream(originalLabels).distinct().count();
            long expectedWeight = exhaustiveForestWeight(graph, components);

            for (MSTResult result : new MSTResult[]{
                    Kruskal.compute(graph), Prim.compute(graph)}) {
                assertEquals(expectedWeight, result.getTotalWeight(),
                        "Exhaustive oracle mismatch in graph " + test);
                assertEquals(components, result.getComponentCount());
                assertEquals(n - components, result.getEdgeCount());

                int[] forestLabels = singletonLabels(n);
                for (var edge : result.getEdges()) {
                    assertTrue(mergeLabels(forestLabels, edge.from, edge.to),
                            "Returned forest contains a cycle");
                }
                for (int u = 0; u < n; u++) {
                    for (int v = 0; v < n; v++) {
                        assertEquals(originalLabels[u] == originalLabels[v],
                                forestLabels[u] == forestLabels[v]);
                    }
                }
            }

            MSTResult kruskal = Kruskal.compute(graph);
            assertEquals(2 * kruskal.getMetrics().getEdgeScans()
                            + kruskal.getEdgeCount(),
                    kruskal.getMetrics().getDsuOps());
            assertEquals(2L * m, Prim.compute(graph).getMetrics().getEdgeScans());
        }
    }

    @Test
    void extremeWeightsUseLongTotalsAndSafeComparisons() {
        Graph graph = new Graph(3, false);
        graph.addEdge(0, 1, Integer.MIN_VALUE);
        graph.addEdge(1, 2, Integer.MIN_VALUE);
        graph.addEdge(0, 2, Integer.MAX_VALUE);

        assertEquals(2L * Integer.MIN_VALUE, Kruskal.compute(graph).getTotalWeight());
        assertEquals(2L * Integer.MIN_VALUE, Prim.compute(graph).getTotalWeight());
    }

    /** Enumerate every forest; use label replacement, independent of the DSU. */
    private static long exhaustiveForestWeight(Graph graph, int components) {
        int neededEdges = graph.vertexCount() - components;
        long minimum = Long.MAX_VALUE;
        int subsets = 1 << graph.edgeCount();
        for (int subset = 0; subset < subsets; subset++) {
            if (Integer.bitCount(subset) != neededEdges) {
                continue;
            }
            int[] labels = singletonLabels(graph.vertexCount());
            long weight = 0;
            boolean acyclic = true;
            for (int i = 0; i < graph.edgeCount(); i++) {
                if ((subset & (1 << i)) == 0) {
                    continue;
                }
                var edge = graph.edges().get(i);
                if (!mergeLabels(labels, edge.from, edge.to)) {
                    acyclic = false;
                    break;
                }
                weight += edge.weight;
            }
            // Every chosen edge belongs to an original component. Acyclicity
            // and n-c edges therefore guarantee that every component is spanned.
            if (acyclic) {
                minimum = Math.min(minimum, weight);
            }
        }
        return minimum;
    }

    private static int[] singletonLabels(int n) {
        int[] labels = new int[n];
        Arrays.setAll(labels, i -> i);
        return labels;
    }

    private static boolean mergeLabels(int[] labels, int u, int v) {
        int from = labels[u];
        int to = labels[v];
        if (from == to) {
            return false;
        }
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] == to) {
                labels[i] = from;
            }
        }
        return true;
    }
}
