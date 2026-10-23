package algorithms;

import graph.Edge;
import graph.Graph;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CondensationTest {

    @Test
    void schedulerFixtureHasExpectedComponentCriticalPath() throws IOException {
        Graph input = Graph.fromFile(Path.of("data/bonus-scheduler.txt"));
        CondensationResult result = Condensation.compute(input);
        SCCResult scc = result.getComponents();
        Graph dag = result.getGraph();

        assertEquals(5, scc.getComponentCount());
        assertTrue(scc.sameComponent(0, 1));
        assertTrue(scc.sameComponent(1, 2));
        assertTrue(scc.sameComponent(3, 4));
        assertTrue(scc.sameComponent(6, 7));
        assertEquals(5, dag.vertexCount());
        assertEquals(5, dag.edgeCount());
        assertEquals(7, weight(dag, scc.getComponentId(0), scc.getComponentId(3)));
        assertEquals(15, result.getMetrics().getEdgeScans());
        assertEquals(15, scc.getMetrics().getEdgeScans());
        assertEquals(15, input.edgeCount());

        DagLongestPathResult paths = DagLongestPath.compute(dag);
        assertEquals(20, paths.getCriticalPathWeight());
        assertEquals(List.of(scc.getComponentId(0), scc.getComponentId(3),
                scc.getComponentId(5), scc.getComponentId(6)), paths.getCriticalPath());
        Kahn.validateOrder(dag, Kahn.compute(dag).getOrder());
    }

    @Test
    void maximumParallelWeightIsPreservedIncludingNegativeAndExtremeValues() {
        Graph input = new Graph(4, true);
        input.addEdge(0, 1, 1);
        input.addEdge(1, 0, 1);
        input.addEdge(0, 2, Integer.MIN_VALUE);
        input.addEdge(1, 2, -4);
        input.addEdge(0, 2, -12);
        input.addEdge(2, 3, Integer.MAX_VALUE);

        CondensationResult result = Condensation.compute(input);
        Graph dag = result.getGraph();
        SCCResult scc = result.getComponents();

        assertEquals(3, dag.vertexCount());
        assertEquals(2, dag.edgeCount());
        assertEquals(-4, weight(dag, scc.getComponentId(0), scc.getComponentId(2)));
        assertEquals(Integer.MAX_VALUE,
                weight(dag, scc.getComponentId(2), scc.getComponentId(3)));
        assertEquals((long) Integer.MAX_VALUE - 4,
                DagLongestPath.compute(dag).getCriticalPathWeight());
    }

    @Test
    void oneStrongComponentBecomesOneIsolatedVertex() {
        Graph input = new Graph(4, true);
        input.addEdge(0, 1, 8);
        input.addEdge(1, 2, 8);
        input.addEdge(2, 3, 8);
        input.addEdge(3, 0, 8);
        input.addEdge(2, 2, Integer.MAX_VALUE);

        CondensationResult result = Condensation.compute(input);
        assertEquals(1, result.getGraph().vertexCount());
        assertEquals(0, result.getGraph().edgeCount());
        assertEquals(5, result.getMetrics().getEdgeScans());
        DagLongestPathResult paths = DagLongestPath.compute(result.getGraph());
        assertEquals(List.of(0), paths.getCriticalPath());
        assertEquals(0, paths.getCriticalPathWeight());
    }

    @Test
    void isolatedVerticesAreRetained() {
        CondensationResult result = Condensation.compute(new Graph(3, true));
        assertEquals(3, result.getGraph().vertexCount());
        assertEquals(0, result.getGraph().edgeCount());
        assertFalse(result.getComponents().sameComponent(0, 1));
        assertFalse(result.getComponents().sameComponent(0, 2));
        assertFalse(result.getComponents().sameComponent(1, 2));
        assertEquals(0, result.getMetrics().getEdgeScans());
    }

    @Test
    void emptyGraphProducesEmptyCondensation() {
        CondensationResult result = Condensation.compute(new Graph(0, true));
        assertEquals(0, result.getGraph().vertexCount());
        assertEquals(0, result.getComponents().getComponentCount());
        assertEquals(0, result.getMetrics().getEdgeScans());
        assertTrue(Kahn.compute(result.getGraph()).isDAG());
        assertEquals(List.of(), DagLongestPath.compute(result.getGraph()).getCriticalPath());
    }

    @Test
    void undirectedGraphIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Condensation.compute(new Graph(3, false)));
    }

    @Test
    void randomCondensationsPreserveReachabilityAndHaveNoCycles() {
        Random random = new Random(1729);
        for (int test = 0; test < 100; test++) {
            int n = 1 + random.nextInt(12);
            Graph input = new Graph(n, true);
            int edges = random.nextInt(n * 3 + 1);
            for (int i = 0; i < edges; i++) {
                input.addEdge(random.nextInt(n), random.nextInt(n), random.nextInt(31) - 15);
            }

            CondensationResult result = Condensation.compute(input);
            Graph dag = result.getGraph();
            assertTrue(Kahn.compute(dag).isDAG(), "Graph " + test);
            boolean[][] before = reachability(input);
            boolean[][] after = reachability(dag);
            for (int u = 0; u < n; u++) {
                for (int v = 0; v < n; v++) {
                    assertEquals(before[u][v], after[result.getComponents().getComponentId(u)]
                            [result.getComponents().getComponentId(v)], "Graph " + test);
                }
            }

            boolean[][] seen = new boolean[dag.vertexCount()][dag.vertexCount()];
            for (Edge edge : dag.edges()) {
                assertNotEquals(edge.from, edge.to);
                assertFalse(seen[edge.from][edge.to], "Duplicate component edge");
                seen[edge.from][edge.to] = true;
                int maximum = input.edges().stream().filter(original ->
                        result.getComponents().getComponentId(original.from) == edge.from
                                && result.getComponents().getComponentId(original.to) == edge.to)
                        .mapToInt(original -> original.weight).max().orElseThrow();
                assertEquals(maximum, edge.weight);
            }
            assertEquals(input.edgeCount(), result.getMetrics().getEdgeScans());
        }
    }

    private static int weight(Graph graph, int from, int to) {
        return graph.neighbors(from).stream().filter(edge -> edge.to == to)
                .findFirst().orElseThrow().weight;
    }

    private static boolean[][] reachability(Graph graph) {
        int n = graph.vertexCount();
        boolean[][] reachable = new boolean[n][n];
        for (int v = 0; v < n; v++) {
            reachable[v][v] = true;
        }
        for (Edge edge : graph.edges()) {
            reachable[edge.from][edge.to] = true;
        }
        for (int k = 0; k < n; k++) {
            for (int u = 0; u < n; u++) {
                for (int v = 0; v < n; v++) {
                    reachable[u][v] |= reachable[u][k] && reachable[k][v];
                }
            }
        }
        return reachable;
    }
}
