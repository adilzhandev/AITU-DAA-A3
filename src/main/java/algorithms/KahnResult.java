
package algorithms;

import metrics.Metrics;

import java.util.Arrays;
import java.util.Objects;

/**
 * Result of Kahn's topological sorting algorithm.
 */
public final class KahnResult {

    private final int[] order;
    private final boolean hasCycle;
    private final Metrics metrics;

    public KahnResult(
            int[] order,
            boolean hasCycle,
            Metrics metrics
    ) {
        this.order = Objects.requireNonNull(
                order, "order"
        ).clone();

        this.hasCycle = hasCycle;
        this.metrics = Objects.requireNonNull(
                metrics, "metrics"
        );
    }

    public int[] getOrder() {
        return order.clone();
    }

    public int getOrderSize() {
        return order.length;
    }

    public boolean hasCycle() {
        return hasCycle;
    }

    public boolean isDAG() {
        return !hasCycle;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public String toString() {
        return "KahnResult{" +
                "order=" + Arrays.toString(order) +
                ", hasCycle=" + hasCycle +
                ", metrics=" + metrics +
                '}';
    }
}
