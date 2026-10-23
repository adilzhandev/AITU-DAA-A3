package algorithms;

import graph.Edge;
import graph.Graph;
import metrics.Metrics;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Tarjan's one-pass algorithm for strongly connected components.
 *
 * <p>Both the DFS call frames and Tarjan's active-vertex stack are explicit,
 * so long paths do not use the Java call stack. A vertex remains active until
 * the root of its component is completed, possibly after its DFS frame has
 * returned. Edges to already completed components do not update lowlink.</p>
 *
 * <p>Time: O(V + E). Extra space: O(V); no transpose is built.
 * Every original edge is scanned once. maxDepth counts active DFS frames,
 * not the separate stack of vertices awaiting component assignment.</p>
 */
public final class Tarjan {

    private Tarjan() {
    }

    public static SCCResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        if (!graph.isDirected()) {
            throw new IllegalArgumentException(
                    "Tarjan requires a directed graph"
            );
        }

        int n = graph.vertexCount();
        Metrics metrics = new Metrics();

        int[] index = new int[n];
        Arrays.fill(index, -1);
        int[] lowlink = new int[n];
        int[] componentIds = new int[n];
        Arrays.fill(componentIds, -1);
        boolean[] onStack = new boolean[n];

        int[] active = new int[n];
        int activeSize = 0;
        int[] frames = new int[n];
        int[] nextEdge = new int[n];
        int nextIndex = 0;
        int componentCount = 0;

        for (int start = 0; start < n; start++) {
            if (index[start] != -1) {
                continue;
            }

            int top = 0;
            frames[top] = start;
            nextEdge[top] = 0;
            index[start] = lowlink[start] = nextIndex++;
            active[activeSize++] = start;
            onStack[start] = true;
            metrics.recordDepth(1);

            while (top >= 0) {
                int vertex = frames[top];
                List<Edge> neighbors = graph.neighbors(vertex);

                if (nextEdge[top] < neighbors.size()) {
                    Edge edge = neighbors.get(nextEdge[top]++);
                    metrics.recordEdgeScan();
                    int to = edge.to;

                    if (index[to] == -1) {
                        // Suspend this frame until the child finishes.
                        frames[++top] = to;
                        nextEdge[top] = 0;
                        index[to] = lowlink[to] = nextIndex++;
                        active[activeSize++] = to;
                        onStack[to] = true;
                        metrics.recordDepth(top + 1);
                    } else if (onStack[to]) {
                        // For a non-tree edge use its target's discovery index.
                        lowlink[vertex] = Math.min(lowlink[vertex], index[to]);
                    }
                } else {
                    if (lowlink[vertex] == index[vertex]) {
                        // The active-stack suffix through this root is one SCC.
                        int member;
                        do {
                            member = active[--activeSize];
                            onStack[member] = false;
                            componentIds[member] = componentCount;
                        } while (member != vertex);
                        componentCount++;
                    }

                    top--;
                    if (top >= 0) {
                        // Equivalent to the return from recursive DFS(child).
                        int parent = frames[top];
                        lowlink[parent] = Math.min(lowlink[parent], lowlink[vertex]);
                    }
                }
            }
        }

        return new SCCResult(componentCount, componentIds, metrics);
    }
}
