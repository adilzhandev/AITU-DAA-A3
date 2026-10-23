
package algorithms;

/**
 * Disjoint Set Union (Union-Find).
 *
 * Optimizations:
 * 1. Path compression
 * 2. Union by size
 *
 * Amortized time: O(alpha(n)) per operation.
 */
public final class DSU {

    private final int[] parent;
    private final int[] size;

    private int componentCount;

    // Operation counters for benchmarking
    private long findCalls;
    private long successfulUnions;

    public DSU(int n) {
        if (n < 0) {
            throw new IllegalArgumentException(
                    "Number of elements cannot be negative"
            );
        }

        parent = new int[n];
        size = new int[n];
        componentCount = n;

        for (int i = 0; i < n; i++) {
            parent[i] = i;
            size[i] = 1;
        }
    }

    /**
     * Creates n singleton sets.
     */
    public static DSU makeSet(int n) {
        return new DSU(n);
    }

    /**
     * Finds the representative of x.
     * Uses iterative full path compression.
     */
    public int find(int x) {
        validate(x);
        findCalls++;

        int root = x;

        // Find the root
        while (root != parent[root]) {
            root = parent[root];
        }

        // Compress the entire path
        while (x != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }

        return root;
    }

    /**
     * Merges the sets containing a and b.
     *
     * Returns true if two different sets
     * were successfully merged.
     */
    public boolean union(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);

        if (rootA == rootB) {
            return false;
        }

        // Union by size
        if (size[rootA] < size[rootB]) {
            int temp = rootA;
            rootA = rootB;
            rootB = temp;
        }

        parent[rootB] = rootA;
        size[rootA] += size[rootB];

        componentCount--;
        successfulUnions++;

        return true;
    }

    /**
     * Returns the current number of components.
     */
    public int components() {
        return componentCount;
    }

    /**
     * Checks whether a and b belong
     * to the same component.
     */
    public boolean connected(int a, int b) {
        return find(a) == find(b);
    }

    /**
     * Returns the size of the component
     * containing x.
     */
    public int componentSize(int x) {
        return size[find(x)];
    }

    public long getFindCalls() {
        return findCalls;
    }

    public long getSuccessfulUnions() {
        return successfulUnions;
    }

    /**
     * Total counted DSU operations.
     */
    public long getDsuOps() {
        return findCalls + successfulUnions;
    }

    /**
     * Reset counters without changing sets.
     */
    public void resetCounters() {
        findCalls = 0;
        successfulUnions = 0;
    }

    private void validate(int x) {
        if (x < 0 || x >= parent.length) {
            throw new IllegalArgumentException(
                    "Invalid element index: " + x
            );
        }
    }
}
