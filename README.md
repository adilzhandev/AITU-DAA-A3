# Assignment 3 — Graph Algorithms

**Adilzhan Kuandykov · SE-2526 · Astana IT University**  
Design and Analysis of Algorithms: city networks and job scheduling.

Repository: [adilzhandev/AITU-DAA-A3](https://github.com/adilzhandev/AITU-DAA-A3).

The project implements DSU, Kruskal and Prim minimum spanning forests, Kosaraju strongly connected components, Kahn topological sorting, and shortest paths in a DAG. Both optional bonuses are implemented: iterative Tarjan SCC and a scheduler that builds the condensation DAG and finds its critical path. All algorithms use the same adjacency-list graph class. Tests cover disconnected graphs, self-loops, parallel edges, isolated vertices, negative DAG weights, and iterative traversal of a path with 100,000 vertices.

Read [REPORT.md](REPORT.md) for the complexity analysis, correctness arguments, measured counter ratios, slopes, algorithm selection table, and both bonus results. The printable [output/pdf/REPORT.pdf](output/pdf/REPORT.pdf) covers the required work and bonuses within five pages.

## Requirements and build

- JDK **17** or newer; Maven compiles with `--release 17`.
- Maven **3.6.3** or newer; the first build needs internet access for dependencies.
- Python **3.11** or newer for charts and the PDF report.
- Bash for the reproduction script; the full benchmark starts with a 256 MiB JVM heap and allows up to 3 GiB.

From the repository root:

```bash
mvn clean verify
java -jar target/graph-benchmark.jar --smoke
java -cp target/graph-benchmark.jar benchmark.BonusBenchmark --smoke
```

`verify` runs the JUnit 5 tests and creates the executable JAR. The required smoke check runs all five benchmark algorithms on the 1,000-vertex sparse configuration. The bonus smoke check compares both SCC algorithms, checks condensation and critical paths on that configuration, and runs the small scheduler demonstration. Both checks validate their results and leave the stored CSV files unchanged. Detailed test reports are in `target/surefire-reports/`.

The tests compare DSU against a simple set reference, the MST algorithms against each other on 100 random connected graphs and against brute force on tiny graphs, SCCs against reachability, and DAG shortest distances against Bellman–Ford, including negative weights. Bonus tests check complete SCC partitions, condensation reachability, and longest paths against exhaustive path enumeration on small DAGs with shuffled vertex labels. All four supplied input files are exercised by the tests. GitHub Actions builds the project and runs both smoke checks on Java 17 for pushes and pull requests.

## Reproduce the experiment and report

Create a Python environment and install the declared plotting/report dependencies once:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r scripts/requirements.txt
```

Then reproduce the build, tests, both complete benchmarks, environment record, five charts, and PDF with one command:

```bash
PYTHON=python bash scripts/reproduce.sh
```

The script stops if any step fails and replaces generated results. It captures the required benchmark console output in `results/benchmark.log`, the bonus output in `results/bonus-benchmark.log`, and records the Java/Maven/Python versions, OS, CPU, JVM flags, configuration, and actual JUnit test totals in `results/environment.json`. The timestamp and timings will change on a new run.

To rebuild only the plots and report from the recorded experiment:

```bash
python scripts/plot_results.py
python scripts/plot_bonus.py
python scripts/build_report.py
```

For benchmark-only runs after building:

```bash
java -Xms256m -Xmx3g -jar target/graph-benchmark.jar
java -Xms256m -Xmx3g -cp target/graph-benchmark.jar benchmark.BonusBenchmark
```

To produce a report for a new experiment, use the full reproduction script so the logs, machine details and test totals are refreshed together. The report rejects a CSV whose SHA-256 differs from its analysis or environment record.

The full benchmark produces **50 data rows** in `results/results.csv`: five algorithms across five sizes (`1000`, `3162`, `10000`, `31623`, `100000`) and two densities (`m = 4n`, `m = 20n`). Each configuration generates three separate graph kinds: connected undirected graphs for MST, directed graphs for SCCs, and DAGs for topological sorting and shortest paths. DSU is measured through Kruskal, so it does not have a separate CSV row. The assignment's detailed ten-configuration, 50-row specification takes precedence over the inconsistent “four generated graphs” phrase in its deliverables list.

Every generator starts a fresh `Random(42)` and follows the assignment's exact shuffle, endpoint, and weight sequence; weights are in `[1, 1000]`, and parallel edges are retained. Each algorithm is measured five times on the same generated graph, with no separate warm-up runs, and `time_ms` is their median. Timing covers the entire `compute` call, including its internal preparation and allocation. Graph generation, correctness checks, distance summation, counter comparisons, and CSV writing occur outside the timed interval.

The benchmark checks every reference MST weight, SCC count, and DAG distance sum, compares both MST algorithms, validates topological orders, checks the required exact scan counts, and checks that repeated results/counters agree. A failed check aborts the run before replacing the CSV. Wall-clock timings remain sensitive to JIT compilation, garbage collection, hardware, and background load; counters are the main evidence.

The bonus benchmark writes **40 additional rows** to `results/bonus.csv`: Kosaraju, Tarjan, condensation, and DAG longest paths across the same ten directed configurations. The required 50-row CSV and its schema remain separate and unchanged by the bonus runner. Bonus timings use the same five-run median convention. Complete SCC partitions must agree up to component relabeling; the checks also verify every contracted edge and its maximum weight, acyclicity, longest distances, and a source-to-sink path witness. For longest-path rows, `n` and `m` describe the condensation DAG; the `graph` label retains the original input size.

## Bonus algorithms and scheduler semantics

Tarjan uses discovery indices, lowlink values, an explicit DFS frame stack, and a separate stack of vertices awaiting component assignment. It makes one DFS pass, scans exactly `m` edges, and needs `O(V)` extra space without building a transpose. Kosaraju scans `2m` edges in its two DFS passes and also builds a transpose. Both algorithms run in `O(V + E)` time; halving the scan counter does not imply a fixed twofold wall-clock speedup.

`Condensation.compute(graph)` maps each SCC to one vertex, discards edges internal to an SCC, and collapses parallel inter-component edges to their maximum weight. This maximum preserves longest-path values between components. A deterministic insertion-ordered hash map gives expected `O(V + E)` construction time, including Tarjan, and `O(V + E)` space. The SCC mapping is available through `getComponents()`, and `getGraph()` returns the resulting DAG.

The scheduler treats each SCC as an atomic group with **zero internal cost**; edge weights are inter-group delays. It computes a critical path in this explicitly defined component model, not a longest simple path in the original cyclic graph. Dependencies inside an SCC cannot be scheduled as an ordinary acyclic precedence chain without this grouping assumption.

`DagLongestPath.compute(dag)` obtains a Kahn topological order and performs maximum relaxation in `O(V + E)` time and `O(V)` extra space. All original zero-indegree vertices start at distance zero; other vertices start at negative infinity. The critical path is the maximum over **sinks**, so a complete source-to-sink path is returned even when every such path has negative weight. An isolated vertex is a valid zero-weight path; an empty graph returns an empty path of weight zero. Cyclic input is rejected. This maximum recurrence is equivalent to shortest paths after sign reversal, but direct addition in `long` arithmetic avoids negating `Integer.MIN_VALUE` and supports totals beyond the `int` range.

Run the small scheduler example without a benchmark:

```bash
java -cp target/graph-benchmark.jar benchmark.BonusBenchmark --demo
```

The [bonus-scheduler.txt](data/bonus-scheduler.txt) fixture has 9 vertices and 15 edges. Its five components are `{0,1,2}`, `{3,4}`, `{5}`, `{6,7}`, and isolated `{8}`. The critical component chain is `{0,1,2} → {3,4} → {5} → {6,7}`, with weight **7 + 5 + 8 = 20**. Component IDs are algorithm labels, not original vertex IDs. Internal loops of weights 99 and 100 do not contribute to this component-level result.

## Counters and plots

The CSV columns are:

```text
algorithm,graph,n,m,time_ms,dsu_ops,edge_scans,comparisons,max_depth,result
```

| Field | What is counted |
| --- | --- |
| `dsu_ops` | Calls to `find` plus successful unions, recorded inside DSU and reported by Kruskal. This counts API work, not each parent-pointer traversal. |
| `edge_scans` | Edges examined by the principal traversal: Kruskal's sorted-edge loop, Prim's adjacency scans, both Kosaraju DFS passes, Tarjan's single DFS pass, condensation's original-edge scan, Kahn's outgoing-edge processing, or DAG relaxation. |
| `comparisons` | Actual weight comparator calls in Kruskal's sort and Prim's priority queue. |
| `max_depth` | Maximum explicit-stack occupancy: DFS frames/pending vertices in Kosaraju's two passes, DFS frames in Tarjan. Tarjan's separate active-vertex stack is excluded. |
| `result` | Required CSV: MST/forest weight, SCC count, topological order size, or the sum of DAG shortest distances. Bonus CSV: SCC count, condensation edge count, or critical-path weight. |

The required scan convention excludes preparatory indegree counting and graph reversal. DAG shortest paths also excludes its internal topological sort from its own `edge_scans`; its runtime includes these prerequisites. Consequently, Kosaraju reports exactly `2m`, Kahn and DAG relaxation exactly `m`, and Prim scans `2m` adjacency entries on the generated undirected graphs. These are deliberately scoped counters, not counts of every primitive instruction. Values can depend on implementation details such as sorting and heap tie handling; the program checks repeatability within a run.

For the bonuses, Tarjan reports `m`, condensation reports `m` original-edge scans, and longest-path relaxation reports `m_c` scans for the condensation DAG's edges. Condensation's runtime includes Tarjan, but its counter reports only contraction; Tarjan's own counters remain available in the SCC result. Longest-path runtime includes Kahn and indegree setup, while its scan counter reports only relaxation.

The plotting script reads the measured CSV and produces:

- [Operations versus n](results/plots/operations-vs-n.png): algorithm work across both densities on logarithmic axes.
- [Scans per edge](results/plots/scans-per-edge.png): normalized traversal work.
- [Kruskal examined-edge share](results/plots/kruskal-examined-share.png): the fraction of sorted edges examined before the spanning tree is complete.

`scripts/plot_bonus.py` adds two figures, for a total of five:

- [Tarjan versus Kosaraju scans](results/plots/scc-scan-comparison.png): measured SCC traversal work across the ten configurations.
- [Condensation and critical path](results/plots/condensation-critical-path.png): the scheduler fixture's components, maximum inter-component weights, and critical chain of weight 20.

Kruskal's sorting cost is visible in `comparisons`; the logarithmic factor does not imply that its `edge_scans` curve must be superlinear. Fitted slopes and stable normalized ratios over five sizes are empirical consistency checks, not proofs of asymptotic complexity. See the report for exact operation definitions and measured values.

## Files and input format

| Location | Contents |
| --- | --- |
| `src/main/java/graph/` | Shared graph, weighted edges, file loading and graph reversal. |
| `src/main/java/algorithms/` | DSU, the five required graph algorithms, and Tarjan, condensation, and DAG longest paths with result objects. |
| `src/main/java/metrics/` | Counters updated inside the algorithms. |
| `src/main/java/benchmark/` | Exact seeded generators, the required benchmark, and the separate bonus benchmark/demo. |
| `src/test/java/` | JUnit 5 correctness, reference, fixture, and stack-safety tests. |
| `data/` | Small graphs for inspection and file-input tests. |
| `results/` | Required and bonus CSV files/logs, environment details, and five plots. |
| `scripts/` | Experiment reproduction, analysis, and report generation. |
| `REPORT.md`, `output/pdf/REPORT.pdf` | Report in Markdown and printable PDF. |

An input file begins with `n m directed` or `n m undirected`, followed by exactly `m` lines containing `u v weight`. Vertices use zero-based indices:

```text
3 2 undirected
0 1 4
1 2 -2
```

The four fixtures are [disconnected-undirected.txt](data/disconnected-undirected.txt), [cyclic-directed.txt](data/cyclic-directed.txt), [dag-negative.txt](data/dag-negative.txt), and [bonus-scheduler.txt](data/bonus-scheduler.txt). Use `Graph.fromFile(Path)` to load them. Both benchmarks generate their large graphs in memory and do not store large generated edge lists; the bonus demo loads the scheduler fixture.

## Scope and Git workflow

The required assignment scope and both optional bonuses are implemented: Tarjan SCC with comparison against Kosaraju, and condensation plus critical-path scheduling.

The submission workflow uses `main` and the original feature branches `feature/dsu`, `feature/mst`, `feature/scc`, `feature/paths`, and `feature/metrics`, with `feature/tarjan`, `feature/critical-path`, and `feature/bonus-metrics` for the bonus work. Release **`v1.1`** includes both bonuses; the original **`v1.0`** tag preserves the required-work baseline. Changes are grouped into meaningful commits for implementation, tests, benchmark methodology, and documentation. Inspect the actual history and refs with:

```bash
git log --graph --decorate --oneline --all
git branch --all
git tag --list
```

Create the Moodle archive from the exact release (the repository link is included in this README):

```bash
mkdir -p submission
git archive --format=zip --prefix=DAA_Assignment3_Adilzhan_Kuandykov_SE-2526/ --output=submission/DAA_Assignment3_Adilzhan_Kuandykov_SE-2526.zip v1.1
```

Upload that ZIP to Moodle. Build outputs, virtual environments, IDE settings and the local `.git` directory are excluded from the archive.

## AI-use acknowledgement

The student supplied the original graph and algorithm implementations. OpenAI Codex assisted with auditing the assignment requirements, existing code and input handling, strengthening tests, improving build and benchmark methodology, generating and interpreting the charts, preparing the report and reproduction scripts, and organizing the submission. The bonus implementation also used Codex assistance for iterative Tarjan, condensation, DAG longest paths, their tests, and the bonus benchmark. This assistance included code and documentation changes. The student is responsible for reviewing the final work and explaining the algorithms, measurements, and conclusions at the defense.
