
package algorithms;

import graph.Edge;
import graph.Graph;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DagShortestPathTest {

    @Test
    void shortestPathsWithNegativeWeights() {
        Graph graph = new Graph(6, true);

        graph.addEdge(0, 2, 5);
        graph.addEdge(1, 2, 2);
        graph.addEdge(2, 3, -4);
        graph.addEdge(1, 4, 7);
        graph.addEdge(3, 4, 3);
        graph.addEdge(4, 5, -2);

        DagShortestPathResult result =
                DagShortestPath.compute(graph);

        assertArrayEquals(
                new long[]{0, 0, 2, -2, 1, -1},
                result.getDistances()
        );

        assertEquals(
                List.of(1, 2, 3, 4, 5),
                result.pathTo(5)
        );

        assertEquals(0, result.sumDistances());
    }

    @Test
    void allZeroIndegreeVerticesAreSources() {
        Graph graph = new Graph(5, true);

        graph.addEdge(0, 2, 10);
        graph.addEdge(1, 2, 3);
        graph.addEdge(2, 3, -2);

        DagShortestPathResult result =
                DagShortestPath.compute(graph);

        assertArrayEquals(
                new long[]{0, 0, 3, 1, 0},
                result.getDistances()
        );

        assertEquals(List.of(4), result.pathTo(4));
    }

    @Test
    void emptyGraphIsSupported() {
        Graph graph = new Graph(0, true);

        DagShortestPathResult result =
                DagShortestPath.compute(graph);

        assertEquals(0, result.getDistances().length);
        assertEquals(0, result.sumDistances());
    }

    @Test
    void cycleIsRejected() {
        Graph graph = new Graph(3, true);

        graph.addEdge(0, 1, 2);
        graph.addEdge(1, 2, -5);
        graph.addEdge(2, 0, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> DagShortestPath.compute(graph)
        );
    }

    @Test
    void undirectedGraphIsRejected() {
        Graph graph = new Graph(3, false);

        assertThrows(
                IllegalArgumentException.class,
                () -> DagShortestPath.compute(graph)
        );
    }

    @Test
    void edgeScansEqualEdgeCount() {
        Graph graph = new Graph(4, true);

        graph.addEdge(0, 1, 4);
        graph.addEdge(0, 2, -2);
        graph.addEdge(1, 3, 3);
        graph.addEdge(2, 3, 6);

        DagShortestPathResult result =
                DagShortestPath.compute(graph);

        assertEquals(
                graph.edgeCount(),
                result.getMetrics().getEdgeScans()
        );
    }

    @Test
    void randomDagsMatchBellmanFord() {
        Random random = new Random(42);

        for (int test = 0; test < 100; test++) {
            int n = 2 + random.nextInt(40);
            Graph graph = new Graph(n, true);

            // A topological rank independent of vertex labels prevents a
            // mistaken numeric-order relaxation from passing the oracle tests.
            int[] rank = new int[n];
            Arrays.setAll(rank, i -> i);
            for (int i = n - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int temporary = rank[i];
                rank[i] = rank[j];
                rank[j] = temporary;
            }

            int m = random.nextInt(n * 5 + 1);

            for (int i = 0; i < m; i++) {
                int a = random.nextInt(n);
                int b = random.nextInt(n);

                if (a == b) {
                    continue;
                }

                if (rank[a] > rank[b]) {
                    int temp = a;
                    a = b;
                    b = temp;
                }

                int weight =
                        random.nextInt(201) - 100;

                graph.addEdge(a, b, weight);
            }

            DagShortestPathResult actual =
                    DagShortestPath.compute(graph);

            long[] expected = bellmanFord(graph);

            assertArrayEquals(
                    expected,
                    actual.getDistances(),
                    "Mismatch in random graph " + test
            );

            assertEquals(
                    graph.edgeCount(),
                    actual.getMetrics().getEdgeScans()
            );

            for (int vertex = 0; vertex < n; vertex++) {
                List<Integer> path = actual.pathTo(vertex);
                assertEquals(vertex, path.get(path.size() - 1).intValue());
                int source = path.get(0);
                assertTrue(graph.edges().stream().noneMatch(edge -> edge.to == source));
                assertEquals(0, actual.distanceTo(source));
                for (int i = 1; i < path.size(); i++) {
                    int u = path.get(i - 1);
                    int v = path.get(i);
                    assertTrue(graph.neighbors(u).stream().anyMatch(edge ->
                            edge.to == v && actual.distanceTo(u) + edge.weight
                                    == actual.distanceTo(v)));
                }
            }
        }
    }

    @Test
    void extremeWeightsAccumulateWithoutIntOverflow() {
        Graph graph = new Graph(5, true);
        graph.addEdge(4, 2, Integer.MIN_VALUE);
        graph.addEdge(2, 0, Integer.MIN_VALUE);
        graph.addEdge(3, 1, Integer.MAX_VALUE);

        DagShortestPathResult result = DagShortestPath.compute(graph);

        assertEquals(2L * Integer.MIN_VALUE, result.distanceTo(0));
        assertEquals(Integer.MAX_VALUE, result.distanceTo(1));
        assertEquals(List.of(4, 2, 0), result.pathTo(0));
    }

    @Test
    void handles100000Vertices() {
        int n = 100_000;

        Graph graph = new Graph(n, true);

        for (int i = 0; i < n - 1; i++) {
            graph.addEdge(i, i + 1, 1);
        }

        DagShortestPathResult result =
                DagShortestPath.compute(graph);

        assertEquals(0, result.distanceTo(0));

        assertEquals(
                n - 1,
                result.distanceTo(n - 1)
        );

        assertEquals(
                n - 1,
                result.getMetrics().getEdgeScans()
        );
    }

    /**
     * Reference Bellman-Ford with a virtual super-source.
     */
    private static long[] bellmanFord(Graph graph) {
        int n = graph.vertexCount();

        int[] indegree = new int[n];

        for (Edge edge : graph.edges()) {
            indegree[edge.to]++;
        }

        long[] dist = new long[n];

        Arrays.fill(
                dist,
                DagShortestPathResult.INF
        );

        for (int v = 0; v < n; v++) {
            if (indegree[v] == 0) {
                dist[v] = 0;
            }
        }

        for (int iteration = 0;
             iteration < n - 1;
             iteration++) {

            boolean changed = false;

            for (Edge edge : graph.edges()) {
                if (dist[edge.from] ==
                        DagShortestPathResult.INF) {
                    continue;
                }

                long candidate =
                        dist[edge.from] + edge.weight;

                if (candidate < dist[edge.to]) {
                    dist[edge.to] = candidate;
                    changed = true;
                }
            }

            if (!changed) {
                break;
            }
        }

        return dist;
    }
}
