package algorithms;

import graph.Edge;
import graph.Graph;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DagLongestPathTest {

    @Test
    void longestPathsUseAllOriginalSources() {
        Graph graph = new Graph(6, true);
        graph.addEdge(0, 2, 5);
        graph.addEdge(1, 2, 2);
        graph.addEdge(2, 3, -4);
        graph.addEdge(1, 4, 7);
        graph.addEdge(3, 4, 3);
        graph.addEdge(4, 5, -2);

        DagLongestPathResult result = DagLongestPath.compute(graph);
        assertArrayEquals(new long[]{0, 0, 5, 1, 7, 5}, result.getDistances());
        assertEquals(List.of(0, 2, 3), result.pathTo(3));
        assertEquals(List.of(1, 4, 5), result.getCriticalPath());
        assertEquals(5, result.getCriticalPathWeight());
        assertEquals(6, result.getMetrics().getEdgeScans());
    }

    @Test
    void negativeCriticalPathMustStillStartAtSourceAndEndAtSink() {
        Graph graph = new Graph(3, true);
        graph.addEdge(0, 1, -7);
        graph.addEdge(1, 2, -3);

        DagLongestPathResult result = DagLongestPath.compute(graph);
        assertArrayEquals(new long[]{0, -7, -10}, result.getDistances());
        assertEquals(List.of(0, 1, 2), result.getCriticalPath());
        assertEquals(-10, result.getCriticalPathWeight());
    }

    @Test
    void maximumPrefixCannotReplaceACompleteSourceToSinkPath() {
        Graph graph = new Graph(4, true);
        graph.addEdge(0, 1, 100);
        graph.addEdge(1, 2, -200);
        graph.addEdge(0, 3, -50);

        DagLongestPathResult result = DagLongestPath.compute(graph);
        assertEquals(List.of(0, 3), result.getCriticalPath());
        assertEquals(-50, result.getCriticalPathWeight());
    }

    @Test
    void isolatedVertexIsAValidZeroWeightSourceToSinkPath() {
        Graph graph = new Graph(3, true);
        graph.addEdge(0, 1, -4);

        DagLongestPathResult result = DagLongestPath.compute(graph);
        assertEquals(List.of(2), result.getCriticalPath());
        assertEquals(0, result.getCriticalPathWeight());
    }

    @Test
    void emptyGraphHasEmptyPathAndZeroWeight() {
        DagLongestPathResult result = DagLongestPath.compute(new Graph(0, true));
        assertArrayEquals(new long[0], result.getDistances());
        assertEquals(List.of(), result.getCriticalPath());
        assertEquals(0, result.getCriticalPathWeight());
        assertEquals(0, result.getMetrics().getEdgeScans());
    }

    @Test
    void cyclesAndSelfLoopsAreRejected() {
        Graph cycle = new Graph(3, true);
        cycle.addEdge(0, 1, 1);
        cycle.addEdge(1, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> DagLongestPath.compute(cycle));
        Graph loop = new Graph(1, true);
        loop.addEdge(0, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> DagLongestPath.compute(loop));
    }

    @Test
    void undirectedGraphIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DagLongestPath.compute(new Graph(2, false)));
    }

    @Test
    void extremeWeightsAccumulateInLongWithoutNegationOrIntOverflow() {
        Graph graph = new Graph(6, true);
        graph.addEdge(5, 3, Integer.MIN_VALUE);
        graph.addEdge(3, 1, Integer.MIN_VALUE);
        graph.addEdge(4, 2, Integer.MAX_VALUE);
        graph.addEdge(2, 0, Integer.MAX_VALUE);

        DagLongestPathResult result = DagLongestPath.compute(graph);
        assertEquals(2L * Integer.MIN_VALUE, result.distanceTo(1));
        assertEquals(List.of(5, 3, 1), result.pathTo(1));
        assertEquals(2L * Integer.MAX_VALUE, result.getCriticalPathWeight());
        assertEquals(List.of(4, 2, 0), result.getCriticalPath());
    }

    @Test
    void parallelEdgesChooseTheLargestWeightAndTiesAreDeterministic() {
        Graph graph = new Graph(4, true);
        graph.addEdge(0, 2, 2);
        graph.addEdge(0, 2, 5);
        graph.addEdge(1, 2, 5);
        graph.addEdge(2, 3, 4);

        DagLongestPathResult result = DagLongestPath.compute(graph);
        assertEquals(9, result.getCriticalPathWeight());
        assertEquals(List.of(0, 2, 3), result.getCriticalPath());
        assertEquals(4, result.getMetrics().getEdgeScans());
    }

    @Test
    void randomShuffledDagsMatchExhaustiveSourceToSinkEnumeration() {
        Random random = new Random(8128);
        for (int test = 0; test < 150; test++) {
            int n = 1 + random.nextInt(8);
            Graph graph = new Graph(n, true);
            int[] order = new int[n];
            Arrays.setAll(order, i -> i);
            for (int i = n - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int temporary = order[i];
                order[i] = order[j];
                order[j] = temporary;
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (random.nextDouble() < 0.32) {
                        graph.addEdge(order[i], order[j], random.nextInt(31) - 15);
                        if (random.nextBoolean()) {
                            graph.addEdge(order[i], order[j], random.nextInt(31) - 15);
                        }
                    }
                }
            }

            int[] indegree = new int[n];
            for (Edge edge : graph.edges()) {
                indegree[edge.to]++;
            }
            long[] expected = new long[n];
            Arrays.fill(expected, Long.MIN_VALUE);
            long[] critical = {Long.MIN_VALUE};
            for (int vertex = 0; vertex < n; vertex++) {
                if (indegree[vertex] == 0) {
                    enumeratePaths(graph, vertex, 0, expected, critical);
                }
            }

            DagLongestPathResult actual = DagLongestPath.compute(graph);
            assertArrayEquals(expected, actual.getDistances(), "Graph " + test);
            assertEquals(critical[0], actual.getCriticalPathWeight(), "Graph " + test);
            assertEquals(graph.edgeCount(), actual.getMetrics().getEdgeScans());
            for (int vertex = 0; vertex < n; vertex++) {
                List<Integer> path = actual.pathTo(vertex);
                assertFalse(path.isEmpty());
                assertEquals(vertex, path.get(path.size() - 1).intValue());
                assertEquals(0, indegree[path.get(0)]);
                for (int i = 1; i < path.size(); i++) {
                    int from = path.get(i - 1);
                    int to = path.get(i);
                    assertTrue(graph.neighbors(from).stream().anyMatch(edge ->
                            edge.to == to && actual.distanceTo(from) + edge.weight
                                    == actual.distanceTo(to)));
                }
            }
            List<Integer> path = actual.getCriticalPath();
            assertTrue(graph.neighbors(path.get(path.size() - 1)).isEmpty());
        }
    }

    @Test
    void handles100000VertexPathWithoutRecursion() {
        int n = 100_000;
        Graph graph = new Graph(n, true);
        for (int vertex = n - 1; vertex > 0; vertex--) {
            graph.addEdge(vertex, vertex - 1, Integer.MAX_VALUE);
        }

        DagLongestPathResult result = DagLongestPath.compute(graph);
        assertEquals((n - 1L) * Integer.MAX_VALUE, result.getCriticalPathWeight());
        List<Integer> path = result.getCriticalPath();
        assertEquals(n, path.size());
        assertEquals(n - 1, path.get(0).intValue());
        assertEquals(0, path.get(n - 1).intValue());
        assertEquals(n - 1, result.getMetrics().getEdgeScans());
    }

    private static void enumeratePaths(Graph graph, int vertex, long total,
                                       long[] distances, long[] critical) {
        distances[vertex] = Math.max(distances[vertex], total);
        if (graph.neighbors(vertex).isEmpty()) {
            critical[0] = Math.max(critical[0], total);
        }
        for (Edge edge : graph.neighbors(vertex)) {
            enumeratePaths(graph, edge.to, total + edge.weight, distances, critical);
        }
    }
}
