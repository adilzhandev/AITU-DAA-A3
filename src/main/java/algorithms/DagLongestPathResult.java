package algorithms;

import metrics.Metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Longest distances, parent paths, and the maximum source-to-sink path. */
public final class DagLongestPathResult {

    public static final long NEG_INF = Long.MIN_VALUE;

    private final long[] distances;
    private final int[] parents;
    private final int criticalSink;
    private final Metrics metrics;

    public DagLongestPathResult(long[] distances, int[] parents,
                               int criticalSink, Metrics metrics) {
        Objects.requireNonNull(distances, "distances");
        Objects.requireNonNull(parents, "parents");
        if (distances.length != parents.length) {
            throw new IllegalArgumentException("Array lengths must match");
        }
        if ((distances.length == 0 && criticalSink != -1)
                || (distances.length > 0
                && (criticalSink < 0 || criticalSink >= distances.length))) {
            throw new IllegalArgumentException("Invalid critical sink: " + criticalSink);
        }
        this.distances = distances.clone();
        this.parents = parents.clone();
        this.criticalSink = criticalSink;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public long distanceTo(int vertex) {
        validateVertex(vertex);
        return distances[vertex];
    }

    public boolean isReachable(int vertex) {
        return distanceTo(vertex) != NEG_INF;
    }

    public int parentOf(int vertex) {
        validateVertex(vertex);
        return parents[vertex];
    }

    public long[] getDistances() {
        return distances.clone();
    }

    public int[] getParents() {
        return parents.clone();
    }

    public Metrics getMetrics() {
        return metrics;
    }

    /** Restores a longest path from an original source to the given vertex. */
    public List<Integer> pathTo(int vertex) {
        validateVertex(vertex);
        if (!isReachable(vertex)) {
            return List.of();
        }
        List<Integer> path = new ArrayList<>();
        int current = vertex;
        while (current != -1) {
            if (current < 0 || current >= parents.length || path.size() >= parents.length) {
                throw new IllegalStateException("Invalid parent chain");
            }
            path.add(current);
            current = parents[current];
        }
        Collections.reverse(path);
        return path;
    }

    public List<Integer> getCriticalPath() {
        return criticalSink == -1 ? List.of() : pathTo(criticalSink);
    }

    public long getCriticalPathWeight() {
        return criticalSink == -1 ? 0 : distances[criticalSink];
    }

    private void validateVertex(int vertex) {
        if (vertex < 0 || vertex >= distances.length) {
            throw new IllegalArgumentException("Invalid vertex: " + vertex);
        }
    }
}
