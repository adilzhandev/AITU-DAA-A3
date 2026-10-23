package algorithms;

import graph.Edge;
import graph.Graph;
import metrics.Metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Contracts each strongly connected component to one vertex.
 *
 * <p>Internal edges are omitted. Parallel edges between the same ordered pair
 * of components are replaced by their maximum weight: this preserves longest
 * path values in the component-level scheduling model. Component internals
 * contribute zero; the result does not preserve longest paths in the original
 * cyclic graph.</p>
 *
 * <p>Tarjan and the contraction take expected O(V + E) time and O(V + E) space
 * (hash-map aggregation). Edge order follows the first occurrence of each
 * component pair in the original input, so repeated runs are deterministic.</p>
 */
public final class Condensation {

    private Condensation() {
    }

    public static CondensationResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");
        if (!graph.isDirected()) {
            throw new IllegalArgumentException("Condensation requires a directed graph");
        }

        SCCResult components = Tarjan.compute(graph);
        Metrics metrics = new Metrics();
        Map<Long, Integer> maximumWeights = new LinkedHashMap<>();

        for (Edge edge : graph.edges()) {
            metrics.recordEdgeScan();
            int from = components.getComponentId(edge.from);
            int to = components.getComponentId(edge.to);
            if (from != to) {
                long pair = ((long) from << 32) | (to & 0xffffffffL);
                maximumWeights.merge(pair, edge.weight, Math::max);
            }
        }

        Graph dag = new Graph(components.getComponentCount(), true);
        for (Map.Entry<Long, Integer> entry : maximumWeights.entrySet()) {
            long pair = entry.getKey();
            dag.addEdge((int) (pair >>> 32), (int) pair, entry.getValue());
        }

        // Only the contraction scan is counted here; Tarjan's metrics remain
        // accessible through getComponents().getMetrics().
        return new CondensationResult(dag, components, metrics);
    }
}
