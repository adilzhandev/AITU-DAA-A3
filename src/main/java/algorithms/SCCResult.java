
package algorithms;

import metrics.Metrics;

import java.util.Arrays;

public final class SCCResult {

    private final int componentCount;
    private final int[] componentIds;
    private final Metrics metrics;

    public SCCResult(
            int componentCount,
            int[] componentIds,
            Metrics metrics
    ) {
        this.componentCount = componentCount;
        this.componentIds = componentIds.clone();
        this.metrics = metrics;
    }

    public int getComponentCount() {
        return componentCount;
    }

    public int getComponentId(int vertex) {
        return componentIds[vertex];
    }

    public int[] getComponentIds() {
        return componentIds.clone();
    }

    public boolean sameComponent(int u, int v) {
        return componentIds[u] == componentIds[v];
    }

    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public String toString() {
        return "SCCResult{" +
                "componentCount=" + componentCount +
                ", componentIds=" +
                Arrays.toString(componentIds) +
                '}';
    }
}
