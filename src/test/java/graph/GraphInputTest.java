package graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GraphInputTest {

    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "3 1\n0 1 2\n",
            "3 0 directed extra\n",
            "three 0 directed\n",
            "-1 0 directed\n",
            "3 -1 directed\n",
            "3 0 unknown\n",
            "3 2 directed\n0 1 2\n",
            "3 0 directed\n0 1 2\n",
            "3 1 directed\n0 1\n",
            "3 1 directed\n0 1 2 extra\n",
            "3 1 directed\n0 1 weight\n",
            "3 1 directed\n0 1 2147483648\n",
            "3 1 directed\n-1 1 2\n",
            "3 1 directed\n0 3 2\n",
            "0 1 directed\n0 0 2\n"
    })
    void malformedFileIsReportedAsIoException(String input) throws IOException {
        Path file = writeInput(input);
        IOException error = assertThrows(IOException.class, () -> Graph.fromFile(file));
        assertFalse(error.getMessage().isBlank());
    }

    @Test
    void whitespaceAndAllIntegerWeightsAreAccepted() throws IOException {
        Graph graph = Graph.fromFile(writeInput(
                "  3\t2   directed  \n"
                        + "0\t1 -2147483648\n"
                        + "1 2 2147483647  \n\n  \t\n"));

        assertEquals(2, graph.edgeCount());
        assertEquals(Integer.MIN_VALUE, graph.edges().get(0).weight);
        assertEquals(Integer.MAX_VALUE, graph.edges().get(1).weight);
    }

    @Test
    void emptyGraphFileIsAccepted() throws IOException {
        Graph graph = Graph.fromFile(writeInput("0 0 undirected\n"));
        assertEquals(0, graph.vertexCount());
        assertEquals(0, graph.edgeCount());
        assertFalse(graph.isDirected());
    }

    private Path writeInput(String input) throws IOException {
        Path file = temporaryDirectory.resolve("graph.txt");
        Files.writeString(file, input);
        return file;
    }
}
