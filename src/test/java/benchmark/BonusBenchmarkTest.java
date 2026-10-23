package benchmark;

import algorithms.Condensation;
import algorithms.CondensationResult;
import algorithms.DagLongestPath;
import algorithms.DagLongestPathResult;
import algorithms.SCCResult;
import graph.Graph;
import metrics.Metrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Fault-injection checks for validation used outside the timed intervals. */
class BonusBenchmarkTest {

    @Test
    void partitionComparisonAcceptsRenamedLabels() {
        assertDoesNotThrow(() -> BonusBenchmark.validatePartitions(
                partition(3, 0, 0, 1, 2, 1), partition(3, 2, 2, 0, 1, 0)));
    }

    @Test
    void partitionComparisonRejectsSameCountWithWrongMembership() {
        assertThrows(AssertionError.class, () -> BonusBenchmark.validatePartitions(
                partition(2, 0, 0, 1, 1), partition(2, 0, 1, 0, 1)));
    }

    @Test
    void partitionComparisonRejectsMergedComponentsAndUnusedLabels() {
        assertThrows(AssertionError.class, () -> BonusBenchmark.validatePartitions(
                partition(2, 0, 1), partition(2, 0, 0)));
        assertThrows(AssertionError.class, () -> BonusBenchmark.validatePartitions(
                partition(3, 0, 1), partition(3, 1, 0)));
    }

    @Test
    void partitionComparisonRejectsInvalidLabelsAndDifferentLengths() {
        assertThrows(AssertionError.class, () -> BonusBenchmark.validatePartitions(
                partition(2, 0, 1), partition(2, 0, 2)));
        assertThrows(AssertionError.class, () -> BonusBenchmark.validatePartitions(
                partition(2, 0, 1), partition(2, 0, 1, 1)));
    }

    @Test
    void condensationValidationRejectsKeepingLighterParallelEdge() {
        Graph original = new Graph(3, true);
        original.addEdge(0, 1, 2);
        original.addEdge(1, 0, 3);
        original.addEdge(0, 2, 4);
        original.addEdge(1, 2, 9);
        assertDoesNotThrow(() -> BonusBenchmark.validateCondensation(
                original, Condensation.compute(original)));

        Graph wrongDag = new Graph(2, true);
        wrongDag.addEdge(0, 1, 4);
        CondensationResult incorrect = new CondensationResult(
                wrongDag, partition(2, 0, 0, 1), new Metrics());
        assertThrows(AssertionError.class,
                () -> BonusBenchmark.validateCondensation(original, incorrect));
    }

    @Test
    void longestValidationAcceptsNegativeEdgesFromActualSources() {
        Graph dag = new Graph(3, true);
        dag.addEdge(0, 1, -8);
        dag.addEdge(1, 2, 3);
        assertDoesNotThrow(() -> BonusBenchmark.validateLongestPaths(
                dag, DagLongestPath.compute(dag)));

        // A fictitious zero-distance restart at vertex 1 yields the wrong 3.
        DagLongestPathResult restarted = new DagLongestPathResult(
                new long[]{0, 0, 3}, new int[]{-1, -1, 1}, 2, new Metrics());
        assertThrows(AssertionError.class,
                () -> BonusBenchmark.validateLongestPaths(dag, restarted));
    }

    @Test
    void longestValidationChecksAllDistancesBeyondCriticalEndpoint() {
        Graph dag = new Graph(3, true);
        dag.addEdge(0, 1, 2);
        dag.addEdge(0, 2, 5);
        DagLongestPathResult incorrect = new DagLongestPathResult(
                new long[]{0, 3, 5}, new int[]{-1, 0, 0}, 2, new Metrics());
        assertThrows(AssertionError.class,
                () -> BonusBenchmark.validateLongestPaths(dag, incorrect));
    }

    @Test
    void longestValidationRejectsAPathThatSkipsAnEdge() {
        Graph dag = new Graph(3, true);
        dag.addEdge(0, 1, 2);
        dag.addEdge(1, 2, 3);
        DagLongestPathResult incorrect = new DagLongestPathResult(
                new long[]{0, 2, 5}, new int[]{-1, 0, 0}, 2, new Metrics());
        assertThrows(AssertionError.class,
                () -> BonusBenchmark.validateLongestPaths(dag, incorrect));
    }

    @Test
    void longestValidationIndependentlyRejectsCycles() {
        Graph cyclic = new Graph(2, true);
        cyclic.addEdge(0, 1, 1);
        cyclic.addEdge(1, 0, 1);
        DagLongestPathResult fake = new DagLongestPathResult(
                new long[]{0, 1}, new int[]{-1, 0}, 1, new Metrics());
        assertThrows(AssertionError.class,
                () -> BonusBenchmark.validateLongestPaths(cyclic, fake));
    }

    @Test
    void longestValidationAcceptsEmptyAndIsolatedGraphs() {
        for (int n : new int[]{0, 1, 4}) {
            Graph dag = new Graph(n, true);
            assertDoesNotThrow(() -> BonusBenchmark.validateLongestPaths(
                    dag, DagLongestPath.compute(dag)));
        }
    }

    private static SCCResult partition(int count, int... labels) {
        return new SCCResult(count, labels, new Metrics());
    }
}
