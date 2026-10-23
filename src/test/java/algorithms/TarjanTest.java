package algorithms;

import graph.Edge;
import graph.Graph;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TarjanTest {

    @Test
    void cyclicFixtureMatchesExpectedPartitionAndKosaraju() throws IOException {
        Graph graph = Graph.fromFile(Path.of("data/cyclic-directed.txt"));
        SCCResult result = Tarjan.compute(graph);

        assertPartition(new int[]{0, 0, 0, 1, 1, 2, 3}, result);
        assertSamePartition(Kosaraju.compute(graph), result);
        assertEquals(graph.edgeCount(), result.getMetrics().getEdgeScans());
    }

    @Test
    void dagFixtureHasSingletonComponents() throws IOException {
        Graph graph = Graph.fromFile(Path.of("data/dag-negative.txt"));
        SCCResult result = Tarjan.compute(graph);

        assertPartition(new int[]{0, 1, 2, 3, 4, 5, 6}, result);
        assertSamePartition(Kosaraju.compute(graph), result);
    }

    @Test
    void selfLoopsParallelEdgesAndIsolatedVerticesAreSupported() {
        Graph graph = new Graph(5, true);
        graph.addEdge(0, 0, -10);
        graph.addEdge(0, 1, 2);
        graph.addEdge(0, 1, 3);
        graph.addEdge(1, 0, -4);
        graph.addEdge(1, 2, 5);
        graph.addEdge(2, 2, 6);
        graph.addEdge(4, 4, 7);

        SCCResult result = Tarjan.compute(graph);

        assertPartition(new int[]{0, 0, 1, 2, 3}, result);
        assertSamePartition(Kosaraju.compute(graph), result);
        assertEquals(graph.edgeCount(), result.getMetrics().getEdgeScans());
    }

    @Test
    void edgeToCompletedComponentDoesNotMergeComponents() {
        Graph graph = new Graph(5, true);
        graph.addEdge(0, 1, 1);
        graph.addEdge(1, 0, 1);
        graph.addEdge(2, 0, 1);
        graph.addEdge(2, 3, 1);
        graph.addEdge(3, 2, 1);
        graph.addEdge(4, 3, 1);

        assertPartition(new int[]{0, 0, 1, 1, 2}, Tarjan.compute(graph));
    }

    @Test
    void edgeToActiveVertexWhoseFrameReturnedStillJoinsComponent() {
        Graph graph = new Graph(4, true);
        graph.addEdge(0, 1, 1);
        graph.addEdge(1, 0, 1);
        graph.addEdge(0, 2, 1);
        graph.addEdge(2, 1, 1);
        graph.addEdge(2, 3, 1);

        assertPartition(new int[]{0, 0, 0, 1}, Tarjan.compute(graph));
    }

    @Test
    void maxDepthCountsDfsFramesNotTarjansActiveStack() {
        Graph graph = new Graph(100, true);
        for (int vertex = 1; vertex < graph.vertexCount(); vertex++) {
            graph.addEdge(0, vertex, 1);
            graph.addEdge(vertex, 0, 1);
        }

        SCCResult result = Tarjan.compute(graph);

        assertEquals(1, result.getComponentCount());
        assertEquals(2, result.getMetrics().getMaxDepth());
        assertEquals(graph.edgeCount(), result.getMetrics().getEdgeScans());
    }

    @Test
    void randomGraphsMatchFullKosarajuPartitionAndBfsOracle() {
        Random random = new Random(73421);

        for (int test = 0; test < 100; test++) {
            int n = random.nextInt(201);
            Graph graph = new Graph(n, true);
            int m = n == 0 ? 0 : random.nextInt(n * 5 + 1);
            for (int i = 0; i < m; i++) {
                graph.addEdge(random.nextInt(n), random.nextInt(n), random.nextInt(21) - 10);
            }

            SCCResult result = Tarjan.compute(graph);
            assertSamePartition(Kosaraju.compute(graph), result);
            assertEquals(m, result.getMetrics().getEdgeScans());

            boolean[][] reachable = new boolean[n][];
            for (int vertex = 0; vertex < n; vertex++) {
                reachable[vertex] = bfs(graph, vertex);
            }
            for (int u = 0; u < n; u++) {
                for (int v = 0; v < n; v++) {
                    assertEquals(reachable[u][v] && reachable[v][u], result.sameComponent(u, v),
                            "Graph " + test + ", vertices " + u + ", " + v);
                }
            }
        }
    }

    @Test
    void pathWith100000VerticesDoesNotUseTheCallStack() {
        int n = 100_000;
        Graph graph = new Graph(n, true);
        for (int vertex = 0; vertex < n - 1; vertex++) {
            graph.addEdge(vertex, vertex + 1, 1);
        }

        SCCResult result = assertDoesNotThrow(() -> Tarjan.compute(graph));

        assertEquals(n, result.getComponentCount());
        assertEquals(n - 1, result.getMetrics().getEdgeScans());
        assertEquals(n, result.getMetrics().getMaxDepth());
        assertSamePartition(Kosaraju.compute(graph), result);
    }

    @Test
    void cycleWith100000VerticesDoesNotUseTheCallStack() {
        int n = 100_000;
        Graph graph = new Graph(n, true);
        for (int vertex = 0; vertex < n; vertex++) {
            graph.addEdge(vertex, (vertex + 1) % n, 1);
        }

        SCCResult result = assertDoesNotThrow(() -> Tarjan.compute(graph));

        assertEquals(1, result.getComponentCount());
        assertEquals(n, result.getMetrics().getEdgeScans());
        assertEquals(n, result.getMetrics().getMaxDepth());
        assertSamePartition(Kosaraju.compute(graph), result);
    }

    @Test
    void isolatedVerticesAreDistinctComponents() {
        SCCResult result = Tarjan.compute(new Graph(5, true));

        assertPartition(new int[]{0, 1, 2, 3, 4}, result);
        assertEquals(0, result.getMetrics().getEdgeScans());
        assertEquals(1, result.getMetrics().getMaxDepth());
    }

    @Test
    void emptyGraphHasNoComponentsOrStackEntries() {
        SCCResult result = Tarjan.compute(new Graph(0, true));

        assertEquals(0, result.getComponentCount());
        assertArrayEquals(new int[0], result.getComponentIds());
        assertEquals(0, result.getMetrics().getEdgeScans());
        assertEquals(0, result.getMetrics().getMaxDepth());
    }

    @Test
    void rejectsUndirectedGraph() {
        assertThrows(IllegalArgumentException.class, () -> Tarjan.compute(new Graph(3, false)));
    }

    @Test
    void rejectsNullGraph() {
        assertThrows(NullPointerException.class, () -> Tarjan.compute(null));
    }

    /** Canonical minimum-vertex labels compare complete partitions in O(V). */
    private static void assertSamePartition(SCCResult expected, SCCResult actual) {
        assertEquals(expected.getComponentCount(), actual.getComponentCount());
        assertArrayEquals(canonicalLabels(expected.getComponentIds()), canonicalLabels(actual.getComponentIds()));
    }

    private static void assertPartition(int[] expectedGroups, SCCResult result) {
        assertArrayEquals(canonicalLabels(expectedGroups), canonicalLabels(result.getComponentIds()));
        assertEquals(Arrays.stream(expectedGroups).distinct().count(), result.getComponentCount());
    }

    private static int[] canonicalLabels(int[] groups) {
        int[] firstVertex = new int[groups.length];
        Arrays.fill(firstVertex, -1);
        int[] labels = new int[groups.length];
        for (int vertex = 0; vertex < groups.length; vertex++) {
            int group = groups[vertex];
            assertTrue(group >= 0 && group < groups.length, "Every vertex needs a valid component ID");
            if (firstVertex[group] == -1) {
                firstVertex[group] = vertex;
            }
            labels[vertex] = firstVertex[group];
        }
        return labels;
    }

    private static boolean[] bfs(Graph graph, int start) {
        boolean[] visited = new boolean[graph.vertexCount()];
        Queue<Integer> queue = new ArrayDeque<>();
        visited[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int vertex = queue.remove();
            for (Edge edge : graph.neighbors(vertex)) {
                if (!visited[edge.to]) {
                    visited[edge.to] = true;
                    queue.add(edge.to);
                }
            }
        }
        return visited;
    }
}
