
package algorithms;

import graph.Edge;
import metrics.Metrics;

import java.util.List;

public final class MSTResult {

    private final long totalWeight;
    private final List<Edge> edges;
    private final int componentCount;
    private final Metrics metrics;

    public MSTResult(
            long totalWeight,
            List<Edge> edges,
            int componentCount,
            Metrics metrics
    ) {
        this.totalWeight = totalWeight;
        this.edges = List.copyOf(edges);
        this.componentCount = componentCount;
        this.metrics = metrics;
    }

    public long getTotalWeight() {
        return totalWeight;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public int getComponentCount() {
        return componentCount;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public String toString() {
        return "MSTResult{" +
                "weight=" + totalWeight +
                ", edges=" + edges.size() +
                ", components=" + componentCount +
                ", metrics=" + metrics +
                '}';
    }
}
