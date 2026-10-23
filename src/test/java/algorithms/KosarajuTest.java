
package algorithms;

import graph.Graph;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class KosarajuTest {

    @Test
    void simpleStronglyConnectedGraph() {
        Graph graph = new Graph(3, true);

        graph.addEdge(0, 1, 1);
        graph.addEdge(1, 2, 1);
        graph.addEdge(2, 0, 1);

        SCCResult result = Kosaraju.compute(graph);

        assertEquals(1, result.getComponentCount());
        assertTrue(result.sameComponent(0, 1));
        assertTrue(result.sameComponent(1, 2));
    }

    @Test
    void findsTwoComponents() {
        Graph graph = new Graph(6, true);

        graph.addEdge(0, 1, 1);
        graph.addEdge(1, 2, 1);
        graph.addEdge(2, 0, 1);

        graph.addEdge(2, 3, 1);

        graph.addEdge(3, 4, 1);
        graph.addEdge(4, 5, 1);
        graph.addEdge(5, 3, 1);

        SCCResult result = Kosaraju.compute(graph);

        assertEquals(2, result.getComponentCount());

        assertTrue(result.sameComponent(0, 2));
        assertTrue(result.sameComponent(3, 5));
        assertFalse(result.sameComponent(0, 3));
    }

    @Test
    void isolatedVerticesAreSeparateComponents() {
        Graph graph = new Graph(5, true);

        SCCResult result = Kosaraju.compute(graph);

        assertEquals(5, result.getComponentCount());
    }

    @Test
    void directedPathHasOneComponentPerVertex() {
        Graph graph = new Graph(4, true);

        graph.addEdge(0, 1, 1);
        graph.addEdge(1, 2, 1);
        graph.addEdge(2, 3, 1);

        SCCResult result = Kosaraju.compute(graph);

        assertEquals(4, result.getComponentCount());
    }

    @Test
    void selfLoopsAndParallelEdgesAreSupported() {
        Graph graph = new Graph(3, true);

        graph.addEdge(0, 0, 1);
        graph.addEdge(0, 1, 2);
        graph.addEdge(0, 1, 3);
        graph.addEdge(1, 0, 4);

        SCCResult result = Kosaraju.compute(graph);

        assertEquals(2, result.getComponentCount());
        assertTrue(result.sameComponent(0, 1));
        assertFalse(result.sameComponent(0, 2));
    }

    @Test
    void edgeScansMustEqualTwiceEdgeCount() {
        Graph graph = new Graph(5, true);

        graph.addEdge(0, 1, 1);
        graph.addEdge(1, 2, 1);
        graph.addEdge(2, 0, 1);
        graph.addEdge(3, 4, 1);

        SCCResult result = Kosaraju.compute(graph);

        assertEquals(
                2L * graph.edgeCount(),
                result.getMetrics().getEdgeScans()
        );
    }

    @Test
    void randomGraphsMatchBruteForce() {
        Random random = new Random(42);

        for (int test = 0; test < 100; test++) {
            int n = 2 + random.nextInt(199);
            Graph graph = new Graph(n, true);

            int m = random.nextInt(n * 5 + 1);

            for (int i = 0; i < m; i++) {
                graph.addEdge(
                        random.nextInt(n),
                        random.nextInt(n),
                        1
                );
            }

            SCCResult result = Kosaraju.compute(graph);

            boolean[][] reachable = new boolean[n][];

            for (int v = 0; v < n; v++) {
                reachable[v] = bfs(graph, v);
            }

            for (int u = 0; u < n; u++) {
                for (int v = 0; v < n; v++) {
                    boolean expected =
                            reachable[u][v] &&
                                    reachable[v][u];

                    assertEquals(
                            expected,
                            result.sameComponent(u, v),
                            "Mismatch in graph " + test +
                                    ", vertices " + u + ", " + v
                    );
                }
            }
        }
    }

    @Test
    void stackSafetyOn100000Vertices() {
        int n = 100_000;

        Graph graph = new Graph(n, true);

        for (int i = 0; i < n - 1; i++) {
            graph.addEdge(i, i + 1, 1);
        }

        SCCResult result = assertDoesNotThrow(
                () -> Kosaraju.compute(graph)
        );

        assertEquals(n, result.getComponentCount());

        assertEquals(
                2L * (n - 1),
                result.getMetrics().getEdgeScans()
        );

        assertEquals(n, result.getMetrics().getMaxDepth());
    }

    @Test
    void emptyGraphHasNoComponentsOrStackEntries() {
        SCCResult result = Kosaraju.compute(new Graph(0, true));

        assertEquals(0, result.getComponentCount());
        assertEquals(0, result.getMetrics().getEdgeScans());
        assertEquals(0, result.getMetrics().getMaxDepth());
    }

    private static boolean[] bfs(Graph graph, int start) {
        int n = graph.vertexCount();

        boolean[] visited = new boolean[n];
        Queue<Integer> queue = new ArrayDeque<>();

        visited[start] = true;
        queue.add(start);

        while (!queue.isEmpty()) {
            int vertex = queue.poll();

            for (var edge : graph.neighbors(vertex)) {
                if (!visited[edge.to]) {
                    visited[edge.to] = true;
                    queue.add(edge.to);
                }
            }
        }

        return visited;
    }
}
