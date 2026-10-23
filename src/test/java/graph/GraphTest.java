
package graph;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphTest {

    @Test
    void undirectedGraphStoresEdgesCorrectly() {
        Graph graph = new Graph(4, false);

        graph.addEdge(0, 1, 10);
        graph.addEdge(1, 2, 20);

        assertEquals(4, graph.vertexCount());
        assertEquals(2, graph.edgeCount());

        assertEquals(1, graph.neighbors(0).size());
        assertEquals(2, graph.neighbors(1).size());
        assertEquals(1, graph.neighbors(2).size());
        assertTrue(graph.neighbors(3).isEmpty());
    }

    @Test
    void directedGraphDoesNotAddReverseEdge() {
        Graph graph = new Graph(3, true);
        graph.addEdge(0, 1, 5);

        assertEquals(1, graph.neighbors(0).size());
        assertTrue(graph.neighbors(1).isEmpty());
    }

    @Test
    void supportsSelfLoopsAndParallelEdges() {
        Graph graph = new Graph(3, false);

        graph.addEdge(0, 0, 10);
        graph.addEdge(0, 1, 5);
        graph.addEdge(0, 1, 7);

        assertEquals(3, graph.edgeCount());
        assertEquals(4, graph.neighbors(0).size());
    }

    @Test
    void reverseGraphWorks() {
        Graph graph = new Graph(3, true);

        graph.addEdge(0, 1, 10);
        graph.addEdge(1, 2, 20);

        Graph reversed = graph.reversed();

        List<Edge> neighbors = reversed.neighbors(2);

        assertEquals(1, neighbors.size());
        assertEquals(1, neighbors.get(0).to);
        assertTrue(reversed.neighbors(0).isEmpty());
    }

    @Test
    void invalidVertexIsRejected() {
        Graph graph = new Graph(3, false);

        assertThrows(
                IllegalArgumentException.class,
                () -> graph.addEdge(0, 5, 10)
        );
    }

    @Test
    void inputFileIsReadCorrectly() throws IOException {
        Path file = Files.createTempFile(
                "graph-test-", ".txt"
        );

        try {
            Files.writeString(file,
                    "3 2 directed\n" +
                            "0 1 10\n" +
                            "1 2 -5\n"
            );

            Graph graph = Graph.fromFile(file);

            assertEquals(3, graph.vertexCount());
            assertEquals(2, graph.edgeCount());
            assertTrue(graph.isDirected());
            assertEquals(-5, graph.edges().get(1).weight);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void edgeListsCannotBeModifiedExternally() {
        Graph graph = new Graph(2, true);
        graph.addEdge(0, 1, 4);

        assertThrows(
                UnsupportedOperationException.class,
                () -> graph.edges().clear()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> graph.neighbors(0).clear()
        );
    }
}
