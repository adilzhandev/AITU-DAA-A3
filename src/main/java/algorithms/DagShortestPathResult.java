
package algorithms;

import metrics.Metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of shortest paths in a DAG.
 */
public final class DagShortestPathResult {

    public static final long INF = Long.MAX_VALUE / 4;

    private final long[] distances;
    private final int[] parent;
    private final Metrics metrics;

    public DagShortestPathResult(
            long[] distances,
            int[] parent,
            Metrics metrics
    ) {
        Objects.requireNonNull(distances);
        Objects.requireNonNull(parent);

        if (distances.length != parent.length) {
            throw new IllegalArgumentException(
                    "Array lengths must match"
            );
        }

        this.distances = distances.clone();
        this.parent = parent.clone();
        this.metrics = Objects.requireNonNull(metrics);
    }

    public long distanceTo(int vertex) {
        validateVertex(vertex);
        return distances[vertex];
    }

    public boolean isReachable(int vertex) {
        return distanceTo(vertex) != INF;
    }

    public int parentOf(int vertex) {
        validateVertex(vertex);
        return parent[vertex];
    }

    public long[] getDistances() {
        return distances.clone();
    }

    public int[] getParents() {
        return parent.clone();
    }

    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * Sum of finite shortest path distances.
     */
    public long sumDistances() {
        long sum = 0;

        for (long distance : distances) {
            if (distance != INF) {
                sum = Math.addExact(sum, distance);
            }
        }

        return sum;
    }

    /**
     * Restores the path from a zero-indegree source.
     */
    public List<Integer> pathTo(int vertex) {
        validateVertex(vertex);

        if (!isReachable(vertex)) {
            return List.of();
        }

        List<Integer> path = new ArrayList<>();

        int current = vertex;
        int steps = 0;

        while (current != -1) {
            path.add(current);
            current = parent[current];

            if (++steps > parent.length) {
                throw new IllegalStateException(
                        "Invalid parent chain"
                );
            }
        }

        Collections.reverse(path);
        return path;
    }

    private void validateVertex(int vertex) {
        if (vertex < 0 || vertex >= distances.length) {
            throw new IllegalArgumentException(
                    "Invalid vertex: " + vertex
            );
        }
    }
}
