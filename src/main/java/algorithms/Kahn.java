
package algorithms;

import graph.Edge;
import graph.Graph;
import metrics.Metrics;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;

/**
 * Kahn's topological sorting algorithm.
 *
 * Uses vertex in-degrees and a FIFO queue.
 *
 * Time complexity: O(V + E)
 * Extra space: O(V)
 */
public final class Kahn {

    private Kahn() {
    }

    public static KahnResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        if (!graph.isDirected()) {
            throw new IllegalArgumentException(
                    "Kahn requires a directed graph"
            );
        }

        int n = graph.vertexCount();
        Metrics metrics = new Metrics();

        int[] indegree = new int[n];

        // Calculate incoming degrees.
        for (Edge edge : graph.edges()) {
            indegree[edge.to]++;
        }

        Queue<Integer> queue = new ArrayDeque<>();

        // All vertices with no incoming edges.
        for (int v = 0; v < n; v++) {
            if (indegree[v] == 0) {
                queue.add(v);
            }
        }

        int[] order = new int[n];
        int count = 0;

        while (!queue.isEmpty()) {
            int vertex = queue.remove();

            order[count++] = vertex;

            // Process every outgoing edge once.
            for (Edge edge : graph.neighbors(vertex)) {
                metrics.recordEdgeScan();

                int to = edge.to;
                indegree[to]--;

                if (indegree[to] == 0) {
                    queue.add(to);
                }
            }
        }

        // Fewer than n processed vertices means a cycle.
        boolean hasCycle = count != n;

        return new KahnResult(
                Arrays.copyOf(order, count),
                hasCycle,
                metrics
        );
    }

    /**
     * Validates a complete topological ordering.
     *
     * Each vertex must appear exactly once.
     * For every edge u -> v, pos[u] < pos[v].
     *
     * Throws if the order is invalid.
     */
    public static void validateOrder(
            Graph graph,
            int[] order
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(order, "order");

        if (!graph.isDirected()) {
            throw new IllegalArgumentException(
                    "Validation requires a directed graph"
            );
        }

        int n = graph.vertexCount();

        if (order.length != n) {
            throw new IllegalArgumentException(
                    "Topological order must contain " +
                            n + " vertices"
            );
        }

        int[] position = new int[n];
        Arrays.fill(position, -1);

        for (int i = 0; i < n; i++) {
            int vertex = order[i];

            if (vertex < 0 || vertex >= n) {
                throw new IllegalArgumentException(
                        "Invalid vertex: " + vertex
                );
            }

            if (position[vertex] != -1) {
                throw new IllegalArgumentException(
                        "Duplicate vertex: " + vertex
                );
            }

            position[vertex] = i;
        }

        for (Edge edge : graph.edges()) {
            if (position[edge.from] >= position[edge.to]) {
                throw new IllegalArgumentException(
                        "Invalid order: edge " +
                                edge.from + " -> " +
                                edge.to + " is violated"
                );
            }
        }
    }
}
