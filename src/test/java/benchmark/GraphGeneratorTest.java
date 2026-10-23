
package benchmark;

import algorithms.*;
import graph.Graph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class GraphGeneratorTest {

    @Test
    void generatesCorrectEdgeCounts() {
        Graph undirected =
                GraphGenerator.generateUndirected(100, 400);

        Graph directed =
                GraphGenerator.generateDirected(100, 400);

        Graph dag =
                GraphGenerator.generateDAG(100, 400);

        assertEquals(100, undirected.vertexCount());
        assertEquals(400, undirected.edgeCount());

        assertEquals(400, directed.edgeCount());
        assertEquals(400, dag.edgeCount());

        assertFalse(undirected.isDirected());
        assertTrue(directed.isDirected());
        assertTrue(dag.isDirected());
    }

    @Test
    void undirectedGraphIsConnected() {
        Graph graph =
                GraphGenerator.generateUndirected(1000, 4000);

        MSTResult result = Kruskal.compute(graph);

        assertEquals(1, result.getComponentCount());
        assertEquals(999, result.getEdgeCount());
    }

    @Test
    void generatedDagHasNoCycles() {
        Graph graph =
                GraphGenerator.generateDAG(1000, 4000);

        KahnResult result = Kahn.compute(graph);

        assertFalse(result.hasCycle());
        assertEquals(1000, result.getOrderSize());

        Kahn.validateOrder(graph, result.getOrder());
    }

    @Test
    void generatorIsDeterministic() {
        assertDeterministic(GraphGenerator::generateUndirected);
        assertDeterministic(GraphGenerator::generateDirected);
        assertDeterministic(GraphGenerator::generateDAG);
    }

    private static void assertDeterministic(
            BiFunction<Integer, Integer, Graph> generator
    ) {
        Graph first = generator.apply(100, 400);
        Graph second = generator.apply(100, 400);

        for (int i = 0; i < 400; i++) {
            assertEquals(
                    first.edges().get(i).from,
                    second.edges().get(i).from
            );

            assertEquals(
                    first.edges().get(i).to,
                    second.edges().get(i).to
            );

            assertEquals(
                    first.edges().get(i).weight,
                    second.edges().get(i).weight
            );
        }
    }

    @ParameterizedTest
    @CsvSource({
            "4000, 147970, 27, 396274",
            "20000, 30363, 1, 252411"
    })
    void referenceValuesFor1000Vertices(
            int m, long expectedMst, int expectedScc, long expectedDag
    ) {
        int n = 1000;

        Graph mstGraph =
                GraphGenerator.generateUndirected(n, m);

        Graph sccGraph =
                GraphGenerator.generateDirected(n, m);

        Graph dag =
                GraphGenerator.generateDAG(n, m);

        MSTResult kruskal = Kruskal.compute(mstGraph);
        MSTResult prim = Prim.compute(mstGraph);

        SCCResult scc = Kosaraju.compute(sccGraph);

        KahnResult kahn = Kahn.compute(dag);

        DagShortestPathResult shortest =
                DagShortestPath.compute(dag);

        // Reference values from Assignment 3 PDF.
        assertEquals(expectedMst, kruskal.getTotalWeight());
        assertEquals(expectedMst, prim.getTotalWeight());

        assertEquals(expectedScc, scc.getComponentCount());

        assertFalse(kahn.hasCycle());
        assertEquals(n, kahn.getOrderSize());

        assertEquals(expectedDag, shortest.sumDistances());

        // Required operation counters.
        assertEquals(
                2L * m,
                scc.getMetrics().getEdgeScans()
        );

        assertEquals(
                m,
                kahn.getMetrics().getEdgeScans()
        );

        assertEquals(
                m,
                shortest.getMetrics().getEdgeScans()
        );
    }

    @Test
    void allGeneratorsHandleASingleIsolatedVertex() {
        assertEquals(0, GraphGenerator.generateUndirected(1, 0).edgeCount());
        assertEquals(0, GraphGenerator.generateDirected(1, 0).edgeCount());
        assertEquals(0, GraphGenerator.generateDAG(1, 0).edgeCount());
    }

    @Test
    void allGeneratorsRejectImpossibleInputs() {
        assertInvalidInputs(GraphGenerator::generateUndirected);
        assertInvalidInputs(GraphGenerator::generateDirected);
        assertInvalidInputs(GraphGenerator::generateDAG);
        assertThrows(IllegalArgumentException.class,
                () -> GraphGenerator.generateUndirected(10, 8));
    }

    private static void assertInvalidInputs(
            BiFunction<Integer, Integer, Graph> generator
    ) {
        assertThrows(IllegalArgumentException.class,
                () -> generator.apply(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> generator.apply(2, -1));
        assertThrows(IllegalArgumentException.class,
                () -> generator.apply(1, 1));
    }
}
