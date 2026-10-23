
package graph;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

/**
 * Weighted graph using adjacency lists.
 *
 * Supports:
 * - Directed and undirected graphs
 * - Self-loops
 * - Parallel edges
 * - Isolated vertices
 * - Negative edge weights
 *
 * Vertices are numbered from 0 to n - 1.
 */
public final class Graph {

    private final int vertexCount;
    private final boolean directed;

    private final List<List<Edge>> adjacency;
    private final List<List<Edge>> adjacencyView;

    // Each original edge is stored exactly once here.
    private final List<Edge> edges;
    private final List<Edge> edgesView;

    public Graph(int vertexCount, boolean directed) {
        if (vertexCount < 0) {
            throw new IllegalArgumentException(
                    "Vertex count cannot be negative"
            );
        }

        this.vertexCount = vertexCount;
        this.directed = directed;

        this.adjacency = new ArrayList<>(vertexCount);
        this.adjacencyView = new ArrayList<>(vertexCount);
        this.edges = new ArrayList<>();

        for (int i = 0; i < vertexCount; i++) {
            List<Edge> neighbors = new ArrayList<>();

            adjacency.add(neighbors);
            adjacencyView.add(
                    Collections.unmodifiableList(neighbors)
            );
        }

        this.edgesView = Collections.unmodifiableList(edges);
    }

    /**
     * Adds an edge to the graph.
     *
     * Undirected edges are stored in both adjacency lists,
     * but only once in the original edge list.
     */
    public void addEdge(int from, int to, int weight) {
        validateVertex(from);
        validateVertex(to);

        Edge edge = new Edge(from, to, weight);

        edges.add(edge);
        adjacency.get(from).add(edge);

        if (!directed) {
            adjacency.get(to).add(
                    new Edge(to, from, weight)
            );
        }
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int edgeCount() {
        return edges.size();
    }

    public boolean isDirected() {
        return directed;
    }

    /**
     * Returns an unmodifiable view of outgoing edges.
     * No list copying is performed.
     */
    public List<Edge> neighbors(int vertex) {
        validateVertex(vertex);
        return adjacencyView.get(vertex);
    }

    /**
     * Returns original edges, each stored exactly once.
     */
    public List<Edge> edges() {
        return edgesView;
    }

    /**
     * Returns a reversed directed graph.
     * Required by Kosaraju's algorithm.
     */
    public Graph reversed() {
        if (!directed) {
            throw new IllegalStateException(
                    "Reversal requires a directed graph"
            );
        }

        Graph result = new Graph(vertexCount, true);

        for (Edge edge : edges) {
            result.addEdge(
                    edge.to,
                    edge.from,
                    edge.weight
            );
        }

        return result;
    }

    /**
     * Reads graph from the assignment input format:
     *
     * n m directed|undirected
     * u v w
     * u v w
     * ...
     */
    public static Graph fromFile(Path path)
            throws IOException {

        Objects.requireNonNull(path, "path");

        try (BufferedReader reader =
                     Files.newBufferedReader(path)) {

            String header = reader.readLine();

            if (header == null) {
                throw new IOException("Empty graph file");
            }

            StringTokenizer tokens =
                    new StringTokenizer(header);

            if (tokens.countTokens() != 3) {
                throw new IOException(
                        "Header must contain: n m directed|undirected"
                );
            }

            int n = parseInt(tokens.nextToken(), 1);
            int m = parseInt(tokens.nextToken(), 1);
            String type = tokens.nextToken();

            if (n < 0 || m < 0) {
                throw new IOException(
                        "Vertex and edge counts cannot be negative"
                );
            }

            boolean directed;

            if (type.equals("directed")) {
                directed = true;
            } else if (type.equals("undirected")) {
                directed = false;
            } else {
                throw new IOException(
                        "Invalid graph type: " + type
                );
            }

            Graph graph = new Graph(n, directed);

            for (int i = 0; i < m; i++) {
                String line = reader.readLine();
                int lineNumber = i + 2;

                if (line == null) {
                    throw new IOException(
                            "Expected " + m
                                    + " edges, found " + i
                    );
                }

                tokens = new StringTokenizer(line);

                if (tokens.countTokens() != 3) {
                    throw new IOException(
                            "Invalid edge at line " + lineNumber
                    );
                }

                int from = parseInt(
                        tokens.nextToken(), lineNumber
                );

                int to = parseInt(
                        tokens.nextToken(), lineNumber
                );

                int weight = parseInt(
                        tokens.nextToken(), lineNumber
                );

                try {
                    graph.addEdge(from, to, weight);
                } catch (IllegalArgumentException ex) {
                    throw new IOException(
                            "Invalid edge at line " + lineNumber,
                            ex
                    );
                }
            }

            String remaining;

            while ((remaining = reader.readLine()) != null) {
                if (!remaining.isBlank()) {
                    throw new IOException(
                            "Unexpected extra data after "
                                    + m + " edges"
                    );
                }
            }

            return graph;
        }
    }

    private static int parseInt(String value, int line)
            throws IOException {

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IOException(
                    "Invalid integer at line " + line
                            + ": " + value,
                    ex
            );
        }
    }

    private void validateVertex(int vertex) {
        if (vertex < 0 || vertex >= vertexCount) {
            throw new IllegalArgumentException(
                    "Vertex " + vertex
                            + " is outside [0, "
                            + vertexCount + ")"
            );
        }
    }
}
