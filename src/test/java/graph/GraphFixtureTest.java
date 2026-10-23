package graph;

import algorithms.DagShortestPath;
import algorithms.DagShortestPathResult;
import algorithms.Kahn;
import algorithms.KahnResult;
import algorithms.Kosaraju;
import algorithms.Kruskal;
import algorithms.MSTResult;
import algorithms.Prim;
import algorithms.SCCResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end checks using the input files included in the submission. */
class GraphFixtureTest {

    @Test
    void disconnectedFileProducesMinimumSpanningForest() throws IOException {
        Graph graph = Graph.fromFile(Path.of("data/disconnected-undirected.txt"));

        assertFalse(graph.isDirected());
        assertEquals(7, graph.vertexCount());
        assertEquals(7, graph.edgeCount());
        assertTrue(graph.neighbors(6).isEmpty());

        MSTResult kruskal = Kruskal.compute(graph);
        MSTResult prim = Prim.compute(graph);

        // Component {0,1,2} costs 1 - 2, {3,4,5} costs 3 + 2;
        // vertex 6 is isolated. The negative self-loop cannot enter a tree.
        for (MSTResult forest : List.of(kruskal, prim)) {
            assertEquals(4, forest.getTotalWeight());
            assertEquals(3, forest.getComponentCount());
            assertEquals(4, forest.getEdgeCount());
            assertTrue(forest.getEdges().stream().noneMatch(e -> e.from == e.to));
        }
    }

    @Test
    void cyclicFileHasExpectedComponentsAndRejectsTopologicalSorting() throws IOException {
        Graph graph = Graph.fromFile(Path.of("data/cyclic-directed.txt"));

        assertTrue(graph.isDirected());
        assertEquals(7, graph.vertexCount());
        assertEquals(9, graph.edgeCount());

        SCCResult scc = Kosaraju.compute(graph);
        assertEquals(4, scc.getComponentCount());
        int[] expectedGroups = {0, 0, 0, 1, 1, 2, 3};
        for (int u = 0; u < graph.vertexCount(); u++) {
            for (int v = 0; v < graph.vertexCount(); v++) {
                assertEquals(expectedGroups[u] == expectedGroups[v], scc.sameComponent(u, v));
            }
        }
        assertEquals(2L * graph.edgeCount(), scc.getMetrics().getEdgeScans());

        KahnResult order = Kahn.compute(graph);
        assertTrue(order.hasCycle());
        assertTrue(order.getOrderSize() < graph.vertexCount());
        assertThrows(IllegalArgumentException.class, () -> DagShortestPath.compute(graph));
    }

    @Test
    void negativeDagFileRestoresPathsFromEverySource() throws IOException {
        Graph graph = Graph.fromFile(Path.of("data/dag-negative.txt"));

        assertTrue(graph.isDirected());
        assertEquals(7, graph.vertexCount());
        assertEquals(8, graph.edgeCount());

        KahnResult topological = Kahn.compute(graph);
        assertFalse(topological.hasCycle());
        assertEquals(graph.vertexCount(), topological.getOrderSize());
        int[] positions = new int[graph.vertexCount()];
        int[] order = topological.getOrder();
        boolean[] seen = new boolean[graph.vertexCount()];
        for (int i = 0; i < order.length; i++) {
            assertFalse(seen[order[i]], "Topological order contains a duplicate vertex");
            seen[order[i]] = true;
            positions[order[i]] = i;
        }
        for (Edge edge : graph.edges()) {
            assertTrue(positions[edge.from] < positions[edge.to]);
        }

        DagShortestPathResult paths = DagShortestPath.compute(graph);
        assertArrayEquals(new long[]{0, 0, 2, -2, 1, -1, 0}, paths.getDistances());
        assertEquals(List.of(1, 2, 3, 4, 5), paths.pathTo(5));
        assertEquals(List.of(0), paths.pathTo(0));
        assertEquals(List.of(6), paths.pathTo(6));
        assertEquals(0, paths.sumDistances());
        assertEquals(graph.edgeCount(), paths.getMetrics().getEdgeScans());
        assertEquals(graph.edgeCount(), topological.getMetrics().getEdgeScans());
        assertEquals(graph.vertexCount(), Kosaraju.compute(graph).getComponentCount());
    }
}
