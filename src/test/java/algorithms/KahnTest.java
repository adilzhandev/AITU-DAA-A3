
package algorithms;

import graph.Graph;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class KahnTest {

    @Test
    void simpleDagProducesValidOrder() {
        Graph graph = new Graph(4, true);

        graph.addEdge(0, 1, 1);
        graph.addEdge(0, 2, 1);
        graph.addEdge(1, 3, 1);
        graph.addEdge(2, 3, 1);

        KahnResult result = Kahn.compute(graph);

        assertFalse(result.hasCycle());
        assertTrue(result.isDAG());
        assertEquals(4, result.getOrderSize());

        assertDoesNotThrow(
                () -> Kahn.validateOrder(
                        graph, result.getOrder()
                )
        );
    }

    @Test
    void directedCycleIsDetected() {
        Graph graph = new Graph(3, true);

        graph.addEdge(0, 1, 1);
        graph.addEdge(1, 2, 1);
        graph.addEdge(2, 0, 1);

        KahnResult result = Kahn.compute(graph);

        assertTrue(result.hasCycle());
        assertFalse(result.isDAG());
        assertEquals(0, result.getOrderSize());
    }

    @Test
    void partiallyProcessedGraphDetectsCycle() {
        Graph graph = new Graph(4, true);

        graph.addEdge(0, 1, 1);
        graph.addEdge(1, 2, 1);
        graph.addEdge(2, 1, 1);
        graph.addEdge(2, 3, 1);

        KahnResult result = Kahn.compute(graph);

        assertTrue(result.hasCycle());
        assertEquals(1, result.getOrderSize());
    }

    @Test
    void disconnectedDagIsSupported() {
        Graph graph = new Graph(6, true);

        graph.addEdge(0, 1, 1);
        graph.addEdge(2, 3, 1);

        KahnResult result = Kahn.compute(graph);

        assertFalse(result.hasCycle());
        assertEquals(6, result.getOrderSize());

        Kahn.validateOrder(graph, result.getOrder());
    }

    @Test
    void selfLoopIsDetectedAsCycle() {
        Graph graph = new Graph(2, true);

        graph.addEdge(0, 0, 5);

        KahnResult result = Kahn.compute(graph);

        assertTrue(result.hasCycle());
    }

    @Test
    void edgeScansEqualEdgeCountForDag() {
        Graph graph = new Graph(5, true);

        graph.addEdge(0, 1, 2);
        graph.addEdge(0, 2, 3);
        graph.addEdge(1, 3, 4);
        graph.addEdge(2, 3, 5);
        graph.addEdge(3, 4, 6);

        KahnResult result = Kahn.compute(graph);

        assertFalse(result.hasCycle());

        assertEquals(
                graph.edgeCount(),
                result.getMetrics().getEdgeScans()
        );
    }

    @Test
    void invalidOrdersAreRejected() {
        Graph graph = new Graph(3, true);

        graph.addEdge(0, 1, 1);
        graph.addEdge(1, 2, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> Kahn.validateOrder(
                        graph, new int[]{2, 1, 0}
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Kahn.validateOrder(
                        graph, new int[]{0, 0, 2}
                )
        );
    }

    @Test
    void randomDagsProduceValidOrders() {
        Random random = new Random(42);

        for (int test = 0; test < 100; test++) {
            int n = 2 + random.nextInt(100);
            Graph graph = new Graph(n, true);

            int m = random.nextInt(n * 5 + 1);

            for (int i = 0; i < m; i++) {
                int a = random.nextInt(n);
                int b = random.nextInt(n);

                if (a == b) {
                    continue;
                }

                // Edges always go from lower
                // to higher vertex number.
                if (a > b) {
                    int temp = a;
                    a = b;
                    b = temp;
                }

                graph.addEdge(a, b, 1);
            }

            KahnResult result = Kahn.compute(graph);

            assertFalse(result.hasCycle());
            assertEquals(n, result.getOrderSize());

            Kahn.validateOrder(
                    graph, result.getOrder()
            );

            assertEquals(
                    graph.edgeCount(),
                    result.getMetrics().getEdgeScans()
            );
        }
    }

    @Test
    void handles100000Vertices() {
        int n = 100_000;

        Graph graph = new Graph(n, true);

        for (int i = 0; i < n - 1; i++) {
            graph.addEdge(i, i + 1, 1);
        }

        KahnResult result = Kahn.compute(graph);

        assertFalse(result.hasCycle());
        assertEquals(n, result.getOrderSize());

        Kahn.validateOrder(
                graph, result.getOrder()
        );

        assertEquals(
                n - 1,
                result.getMetrics().getEdgeScans()
        );
    }

    @Test
    void undirectedGraphIsRejected() {
        Graph graph = new Graph(3, false);

        assertThrows(
                IllegalArgumentException.class,
                () -> Kahn.compute(graph)
        );
    }
}
