package algorithms;

import graph.Edge;
import graph.Graph;
import metrics.Metrics;

import java.util.Arrays;
import java.util.Objects;

/**
 * Longest paths from all original zero-indegree sources in a DAG.
 *
 * <p>The critical path ends at a sink, including when every nonempty path has
 * negative weight. An isolated vertex is a valid source-to-sink path of weight
 * zero. Edge weights are accumulated directly in long arithmetic; negating
 * integer weights would overflow for Integer.MIN_VALUE.</p>
 *
 * <p>Time O(V + E), additional space O(V), including Kahn's topological sort.
 * Metrics count only the relaxation pass; Kahn and indegree setup are separate.
 * Empty input has an empty critical path and weight zero.</p>
 */
public final class DagLongestPath {

    private DagLongestPath() {
    }

    public static DagLongestPathResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");
        if (!graph.isDirected()) {
            throw new IllegalArgumentException("DAG longest paths require a directed graph");
        }

        KahnResult topological = Kahn.compute(graph);
        if (topological.hasCycle()) {
            throw new IllegalArgumentException("Longest paths require a DAG; cycle detected");
        }

        int n = graph.vertexCount();
        int[] indegree = new int[n];
        for (Edge edge : graph.edges()) {
            indegree[edge.to]++;
        }

        long[] distances = new long[n];
        int[] parents = new int[n];
        Arrays.fill(distances, DagLongestPathResult.NEG_INF);
        Arrays.fill(parents, -1);
        for (int vertex = 0; vertex < n; vertex++) {
            if (indegree[vertex] == 0) {
                distances[vertex] = 0;
            }
        }

        Metrics metrics = new Metrics();
        for (int from : topological.getOrder()) {
            for (Edge edge : graph.neighbors(from)) {
                metrics.recordEdgeScan();
                if (distances[from] == DagLongestPathResult.NEG_INF) {
                    continue;
                }
                long candidate = distances[from] + edge.weight;
                if (candidate > distances[edge.to]) {
                    distances[edge.to] = candidate;
                    parents[edge.to] = from;
                }
            }
        }

        int criticalSink = -1;
        for (int vertex = 0; vertex < n; vertex++) {
            if (graph.neighbors(vertex).isEmpty()
                    && (criticalSink == -1
                    || distances[vertex] > distances[criticalSink])) {
                criticalSink = vertex;
            }
        }
        return new DagLongestPathResult(distances, parents, criticalSink, metrics);
    }
}
