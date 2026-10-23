
package algorithms;

import graph.Edge;
import graph.Graph;
import metrics.Metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimum spanning forest by sorting edges and merging distinct DSU sets.
 * Time: O(V + E log(E + 1) + E alpha(V)); extra space: O(V + E).
 * edgeScans counts candidates until a connected tree is complete, whereas
 * comparisons includes sorting the entire edge list before that early exit.
 */
public final class Kruskal {

    private Kruskal() {
    }

    public static MSTResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        if (graph.isDirected()) {
            throw new IllegalArgumentException(
                    "Kruskal requires an undirected graph"
            );
        }

        int n = graph.vertexCount();

        Metrics metrics = new Metrics();
        DSU dsu = new DSU(n);

        List<Edge> sortedEdges =
                new ArrayList<>(graph.edges());

        // Count actual weight comparisons during sorting.
        sortedEdges.sort((a, b) -> {
            metrics.recordComparison();
            return Integer.compare(a.weight, b.weight);
        });

        List<Edge> selectedEdges = new ArrayList<>();
        long totalWeight = 0;

        for (Edge edge : sortedEdges) {
            metrics.recordEdgeScan();

            // union returns false if a cycle would form.
            if (dsu.union(edge.from, edge.to)) {
                selectedEdges.add(edge);
                totalWeight += edge.weight;

                // Connected MST is complete.
                if (selectedEdges.size() == n - 1) {
                    break;
                }
            }
        }

        metrics.addDsuOps(dsu.getDsuOps());

        return new MSTResult(
                totalWeight,
                selectedEdges,
                dsu.components(),
                metrics
        );
    }
}
