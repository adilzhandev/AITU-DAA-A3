package algorithms;

import graph.Graph;
import metrics.Metrics;

import java.util.Objects;

/** Condensation DAG together with its original-vertex-to-component mapping. */
public final class CondensationResult {

    private final Graph graph;
    private final SCCResult components;
    private final Metrics metrics;

    public CondensationResult(Graph graph, SCCResult components, Metrics metrics) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.components = Objects.requireNonNull(components, "components");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Returns the newly built DAG; component IDs are its vertex IDs. */
    public Graph getGraph() {
        return graph;
    }

    public SCCResult getComponents() {
        return components;
    }

    /** Counts original edges inspected during contraction, excluding SCC work. */
    public Metrics getMetrics() {
        return metrics;
    }
}
