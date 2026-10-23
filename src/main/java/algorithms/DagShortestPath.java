
package algorithms;

import graph.Edge;
import graph.Graph;
import metrics.Metrics;

import java.util.Arrays;
import java.util.Objects;

/**
 * Shortest paths in a Directed Acyclic Graph.
 *
 * All zero-indegree vertices act as sources
 * with initial distance 0.
 *
 * Negative edge weights are supported.
 *
 * Time complexity: O(V + E)
 * Extra space: O(V)
 */
public final class DagShortestPath {

    private DagShortestPath() {
    }

    public static DagShortestPathResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        if (!graph.isDirected()) {
            throw new IllegalArgumentException(
                    "DAG shortest paths require a directed graph"
            );
        }

        int n = graph.vertexCount();

        Metrics metrics = new Metrics();

        // Obtain a topological ordering. Its metrics are deliberately separate:
        // this algorithm reports scans in the relaxation pass only.
        KahnResult topological = Kahn.compute(graph);

        if (topological.hasCycle()) {
            throw new IllegalArgumentException(
                    "Shortest paths require a DAG; cycle detected"
            );
        }

        int[] order = topological.getOrder();

        // Compute original vertex in-degrees.
        int[] indegree = new int[n];

        for (Edge edge : graph.edges()) {
            indegree[edge.to]++;
        }

        long[] dist = new long[n];
        int[] parent = new int[n];

        Arrays.fill(
                dist,
                DagShortestPathResult.INF
        );

        Arrays.fill(parent, -1);

        // Virtual super-source:
        // every original zero-indegree vertex starts at 0.
        for (int v = 0; v < n; v++) {
            if (indegree[v] == 0) {
                dist[v] = 0;
            }
        }

        // Relax every outgoing edge exactly once.
        for (int u : order) {

            for (Edge edge : graph.neighbors(u)) {
                metrics.recordEdgeScan();

                int v = edge.to;

                if (dist[u] ==
                        DagShortestPathResult.INF) {
                    continue;
                }

                long candidate = dist[u] + edge.weight;

                if (candidate < dist[v]) {
                    dist[v] = candidate;
                    parent[v] = u;
                }
            }
        }

        return new DagShortestPathResult(
                dist,
                parent,
                metrics
        );
    }
}
