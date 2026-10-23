
package algorithms;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DSUTest {

    @Test
    void initiallyEveryElementIsSeparate() {
        DSU dsu = new DSU(5);

        assertEquals(5, dsu.components());

        for (int i = 0; i < 5; i++) {
            assertEquals(i, dsu.find(i));
            assertEquals(1, dsu.componentSize(i));
        }
    }

    @Test
    void unionMergesComponents() {
        DSU dsu = new DSU(5);

        assertTrue(dsu.union(0, 1));
        assertTrue(dsu.union(1, 2));

        assertEquals(3, dsu.components());

        assertTrue(dsu.connected(0, 2));
        assertFalse(dsu.connected(0, 3));

        assertEquals(3, dsu.componentSize(0));
    }

    @Test
    void duplicateUnionDoesNotChangeComponents() {
        DSU dsu = new DSU(4);

        assertTrue(dsu.union(0, 1));
        assertFalse(dsu.union(1, 0));

        assertEquals(3, dsu.components());
        assertEquals(1, dsu.getSuccessfulUnions());
    }

    @Test
    void selfUnionDoesNotChangeComponents() {
        DSU dsu = new DSU(3);

        assertFalse(dsu.union(1, 1));
        assertEquals(3, dsu.components());
    }

    @Test
    void operationCountersAreCorrect() {
        DSU dsu = new DSU(4);

        dsu.union(0, 1);
        dsu.union(0, 1);
        dsu.find(2);

        assertEquals(5, dsu.getFindCalls());
        assertEquals(1, dsu.getSuccessfulUnions());
        assertEquals(6, dsu.getDsuOps());

        dsu.resetCounters();

        assertEquals(0, dsu.getDsuOps());
        assertEquals(3, dsu.components());
    }

    @Test
    void invalidIndicesAreRejected() {
        DSU dsu = new DSU(3);

        assertThrows(
                IllegalArgumentException.class,
                () -> dsu.find(-1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> dsu.union(0, 10)
        );
    }

    @Test
    void handlesLargeNumberOfElements() {
        int n = 100_000;

        DSU dsu = new DSU(n);

        for (int i = 1; i < n; i++) {
            dsu.union(0, i);
        }

        assertEquals(1, dsu.components());
        assertEquals(n, dsu.componentSize(n - 1));
    }

    @Test
    void randomUnionsMatchReference() {
        int n = 100;
        int operations = 1000;

        DSU dsu = new DSU(n);

        // Simple reference implementation
        int[] labels = new int[n];

        for (int i = 0; i < n; i++) {
            labels[i] = i;
        }

        Random random = new Random(42);

        for (int k = 0; k < operations; k++) {
            int a = random.nextInt(n);
            int b = random.nextInt(n);

            int labelA = labels[a];
            int labelB = labels[b];

            boolean expectedMerge = labelA != labelB;
            boolean actualMerge = dsu.union(a, b);

            assertEquals(expectedMerge, actualMerge);

            if (expectedMerge) {
                for (int i = 0; i < n; i++) {
                    if (labels[i] == labelB) {
                        labels[i] = labelA;
                    }
                }
            }

            int expectedComponents = 0;

            for (int i = 0; i < n; i++) {
                boolean firstOccurrence = true;

                for (int j = 0; j < i; j++) {
                    if (labels[i] == labels[j]) {
                        firstOccurrence = false;
                        break;
                    }
                }

                if (firstOccurrence) {
                    expectedComponents++;
                }
            }

            assertEquals(
                    expectedComponents,
                    dsu.components()
            );

            for (int i = 0; i < n; i++) {
                assertEquals(
                        labels[i] == labels[a],
                        dsu.find(i) == dsu.find(a)
                );
            }
        }
    }
}
