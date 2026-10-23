
package algorithms;

import graph.Edge;
import graph.Graph;
import metrics.Metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Lazy priority-queue Prim, restarted at each unvisited component.
 * Time: O(V + E log(E + 1)); extra space: O(V + E).
 * Parallel edges mean the heap may contain O(E), rather than O(V), entries.
 * Each undirected adjacency entry is scanned once, including both entries
 * of a self-loop, so edgeScans is exactly 2E.
 */
public final class Prim {

    private Prim() {
    }

    public static MSTResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        if (graph.isDirected()) {
            throw new IllegalArgumentException(
                    "Prim requires an undirected graph"
            );
        }

        int n = graph.vertexCount();

        Metrics metrics = new Metrics();
        boolean[] visited = new boolean[n];

        PriorityQueue<Edge> pq =
                new PriorityQueue<>((a, b) -> {
                    metrics.recordComparison();
                    return Integer.compare(a.weight, b.weight);
                });

        List<Edge> selectedEdges = new ArrayList<>();

        long totalWeight = 0;
        int componentCount = 0;

        // Starts at vertex 0.
        // Other starts handle disconnected graphs.
        for (int start = 0; start < n; start++) {

            if (visited[start]) {
                continue;
            }

            componentCount++;
            visited[start] = true;

            addOutgoingEdges(
                    graph, start, visited, pq, metrics
            );

            while (!pq.isEmpty()) {
                Edge edge = pq.poll();

                if (visited[edge.to]) {
                    continue;
                }

                visited[edge.to] = true;

                selectedEdges.add(edge);
                totalWeight += edge.weight;

                addOutgoingEdges(
                        graph,
                        edge.to,
                        visited,
                        pq,
                        metrics
                );
            }
        }

        return new MSTResult(
                totalWeight,
                selectedEdges,
                componentCount,
                metrics
        );
    }

    private static void addOutgoingEdges(
            Graph graph,
            int vertex,
            boolean[] visited,
            PriorityQueue<Edge> pq,
            Metrics metrics
    ) {
        for (Edge edge : graph.neighbors(vertex)) {
            metrics.recordEdgeScan();

            if (!visited[edge.to]) {
                pq.offer(edge);
            }
        }
    }
}
