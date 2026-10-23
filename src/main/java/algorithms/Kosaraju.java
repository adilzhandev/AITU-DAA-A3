
package algorithms;

import graph.Edge;
import graph.Graph;
import metrics.Metrics;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Kosaraju's algorithm for Strongly Connected Components.
 *
 * Uses iterative DFS to avoid StackOverflowError.
 *
 * Time complexity: O(V + E)
 * Extra space: O(V + E)
 */
public final class Kosaraju {

    private Kosaraju() {
    }

    public static SCCResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        if (!graph.isDirected()) {
            throw new IllegalArgumentException(
                    "Kosaraju requires a directed graph"
            );
        }

        int n = graph.vertexCount();
        Metrics metrics = new Metrics();

        boolean[] visited = new boolean[n];

        // Vertices ordered by DFS finishing time.
        int[] finishOrder = new int[n];
        int orderSize = 0;

        // Explicit DFS stack.
        int[] stack = new int[n];
        int[] nextEdge = new int[n];

        // First pass: compute finishing order.
        for (int start = 0; start < n; start++) {
            if (visited[start]) {
                continue;
            }

            int top = 0;

            stack[top] = start;
            nextEdge[top] = 0;
            visited[start] = true;

            metrics.recordDepth(1);

            while (top >= 0) {
                int vertex = stack[top];
                List<Edge> neighbors =
                        graph.neighbors(vertex);

                if (nextEdge[top] < neighbors.size()) {
                    Edge edge =
                            neighbors.get(nextEdge[top]++);

                    metrics.recordEdgeScan();

                    int to = edge.to;

                    if (!visited[to]) {
                        visited[to] = true;

                        top++;
                        stack[top] = to;
                        nextEdge[top] = 0;

                        metrics.recordDepth(top + 1);
                    }
                } else {
                    // All outgoing edges processed.
                    finishOrder[orderSize++] = vertex;
                    top--;
                }
            }
        }

        // Reverse all directed edges. Construction is not a DFS edge scan.
        Graph reversed = graph.reversed();

        int[] componentIds = new int[n];
        Arrays.fill(componentIds, -1);

        int componentCount = 0;

        // Second pass in decreasing finishing order.
        for (int i = orderSize - 1; i >= 0; i--) {
            int start = finishOrder[i];

            if (componentIds[start] != -1) {
                continue;
            }

            int top = 0;
            stack[top] = start;
            componentIds[start] = componentCount;

            metrics.recordDepth(1);

            // Here depth is the number of pending vertices on the explicit
            // stack (the first pass instead keeps active DFS frames).
            while (top >= 0) {
                int vertex = stack[top--];

                for (Edge edge : reversed.neighbors(vertex)) {
                    metrics.recordEdgeScan();

                    int to = edge.to;

                    if (componentIds[to] == -1) {
                        componentIds[to] = componentCount;
                        stack[++top] = to;

                        metrics.recordDepth(top + 1);
                    }
                }
            }

            componentCount++;
        }

        return new SCCResult(
                componentCount,
                componentIds,
                metrics
        );
    }
}
