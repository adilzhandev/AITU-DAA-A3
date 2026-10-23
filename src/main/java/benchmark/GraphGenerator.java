
package benchmark;

import graph.Graph;

import java.util.Random;

/**
 * Reproducible graph generator for Assignment 3.
 *
 * Every generation starts with Random(42).
 */
public final class GraphGenerator {

    private static final long SEED = 42L;

    private GraphGenerator() {
    }

    /**
     * Generates a connected undirected graph.
     *
     * First constructs a random spanning tree,
     * then adds random edges until m edges exist.
     */
    public static Graph generateUndirected(int n, int m) {
        validate(n, m);

        if (m < n - 1) {
            throw new IllegalArgumentException(
                    "Connected graph requires m >= n - 1"
            );
        }

        Random random = new Random(SEED);
        Graph graph = new Graph(n, false);

        int[] permutation = permutation(n, random);

        // Random spanning tree
        for (int i = 1; i < n; i++) {
            int u = permutation[random.nextInt(i)];
            int v = permutation[i];

            int weight = randomWeight(random);
            graph.addEdge(u, v, weight);
        }

        // Additional random edges
        while (graph.edgeCount() < m) {
            int a = random.nextInt(n);
            int b = random.nextInt(n);

            while (a == b) {
                b = random.nextInt(n);
            }

            int weight = randomWeight(random);
            graph.addEdge(a, b, weight);
        }

        return graph;
    }

    /**
     * Generates a directed graph.
     * Cycles are allowed.
     */
    public static Graph generateDirected(int n, int m) {
        validate(n, m);

        Random random = new Random(SEED);
        Graph graph = new Graph(n, true);

        for (int i = 0; i < m; i++) {
            int a = random.nextInt(n);
            int b = random.nextInt(n);

            while (a == b) {
                b = random.nextInt(n);
            }

            int weight = randomWeight(random);
            graph.addEdge(a, b, weight);
        }

        return graph;
    }

    /**
     * Generates a Directed Acyclic Graph.
     *
     * A random permutation defines vertex ranks.
     * Every edge goes from a lower rank
     * to a higher rank.
     */
    public static Graph generateDAG(int n, int m) {
        validate(n, m);

        Random random = new Random(SEED);
        Graph graph = new Graph(n, true);

        int[] rank = permutation(n, random);

        for (int i = 0; i < m; i++) {
            int a = random.nextInt(n);
            int b = random.nextInt(n);

            while (a == b) {
                b = random.nextInt(n);
            }

            if (rank[a] > rank[b]) {
                int temp = a;
                a = b;
                b = temp;
            }

            int weight = randomWeight(random);
            graph.addEdge(a, b, weight);
        }

        return graph;
    }

    /**
     * Fisher-Yates shuffle.
     */
    private static int[] permutation(
            int n,
            Random random
    ) {
        int[] values = new int[n];

        for (int i = 0; i < n; i++) {
            values[i] = i;
        }

        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);

            int temp = values[i];
            values[i] = values[j];
            values[j] = temp;
        }

        return values;
    }

    private static int randomWeight(Random random) {
        return 1 + random.nextInt(1000);
    }

    private static void validate(int n, int m) {
        if (n < 1 || m < 0) {
            throw new IllegalArgumentException(
                    "Invalid graph size: n=" + n + ", m=" + m
            );
        }

        if (n == 1 && m > 0) {
            throw new IllegalArgumentException(
                    "Cannot generate non-self-loop edges "
                            + "with only one vertex"
            );
        }
    }
}
