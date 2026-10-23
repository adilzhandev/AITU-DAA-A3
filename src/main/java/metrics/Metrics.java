
package metrics;

/**
 * Operation counters used by graph algorithms.
 *
 * <p>These are phase counters, not an instruction count: edgeScans counts
 * Kruskal's sorted candidates, Prim's adjacency visits, Kosaraju's two DFS
 * passes, Kahn's outgoing-edge removals, or the DAG relaxation pass.
 * Building a transpose, computing initial in-degrees, and the separate Kahn
 * call made by DAG shortest paths are excluded. Thus a DAG has m scans in
 * either Kahn or relaxation, and Kosaraju has 2m scans.</p>
 *
 * <p>comparisons counts weight-comparator calls in sorting and the priority
 * queue. dsuOps is find calls plus successful unions. maxDepth is the largest
 * number of entries simultaneously held by an explicit DFS stack; it is not
 * an operation total and must not be added to the other counters.</p>
 */
public final class Metrics {

    private long dsuOps;
    private long edgeScans;
    private long comparisons;
    private int maxDepth;

    public void addDsuOps(long count) {
        dsuOps += count;
    }

    public void recordEdgeScan() {
        edgeScans++;
    }

    public void recordComparison() {
        comparisons++;
    }

    public void recordDepth(int depth) {
        maxDepth = Math.max(maxDepth, depth);
    }

    public long getDsuOps() {
        return dsuOps;
    }

    public long getEdgeScans() {
        return edgeScans;
    }

    public long getComparisons() {
        return comparisons;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void reset() {
        dsuOps = 0;
        edgeScans = 0;
        comparisons = 0;
        maxDepth = 0;
    }

    @Override
    public String toString() {
        return "Metrics{" +
                "dsuOps=" + dsuOps +
                ", edgeScans=" + edgeScans +
                ", comparisons=" + comparisons +
                ", maxDepth=" + maxDepth +
                '}';
    }
}
