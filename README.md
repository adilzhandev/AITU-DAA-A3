# Assignment 3 — Graph Algorithms

**Adilzhan Kuandykov · SE-2526 · Astana IT University**  
Design and Analysis of Algorithms: city networks and job scheduling.

Repository: [adilzhandev/AITU-DAA-A3](https://github.com/adilzhandev/AITU-DAA-A3).

The project implements DSU, Kruskal and Prim minimum spanning forests, Kosaraju strongly connected components, Kahn topological sorting, and shortest paths in a DAG. All algorithms use the same adjacency-list graph class. Tests cover disconnected graphs, self-loops, parallel edges, isolated vertices, negative DAG weights, and iterative traversal of a path with 100,000 vertices.

Read [REPORT.md](REPORT.md) for the complexity analysis, correctness arguments, measured counter ratios, slopes, and algorithm selection table. A printable version is available at [output/pdf/REPORT.pdf](output/pdf/REPORT.pdf).

## Requirements and build

- JDK **17** or newer; Maven compiles with `--release 17`.
- Maven **3.6.3** or newer; the first build needs internet access for dependencies.
- Python **3.11** or newer for charts and the PDF report.
- Bash for the reproduction script; the full benchmark starts with a 256 MiB JVM heap and allows up to 3 GiB.

From the repository root:

```bash
mvn clean verify
java -jar target/graph-benchmark.jar --smoke
```

`verify` runs the JUnit 5 tests and creates the executable JAR. The smoke check runs all five benchmark algorithms on the 1,000-vertex sparse configuration, validates the reference results, and leaves the stored CSV unchanged. Detailed test reports are in `target/surefire-reports/`.

The tests compare DSU against a simple set reference, the MST algorithms against each other on 100 random connected graphs and against brute force on tiny graphs, SCCs against reachability, and DAG distances against Bellman–Ford, including negative weights. The three supplied input files are exercised by the tests. GitHub Actions builds the project and runs this smoke check on Java 17 for pushes and pull requests.

## Reproduce the experiment and report

Create a Python environment and install the declared plotting/report dependencies once:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r scripts/requirements.txt
```

Then reproduce the build, tests, complete benchmark, environment record, charts, and PDF with one command:

```bash
PYTHON=python bash scripts/reproduce.sh
```

The script stops if any step fails and replaces generated results. It captures the benchmark console output in `results/benchmark.log` and records the Java/Maven/Python versions, OS, CPU, JVM flags, configuration, and actual JUnit test totals in `results/environment.json`. The timestamp and timings will change on a new run.

To rebuild only the plots and report from the recorded experiment:

```bash
python scripts/plot_results.py
python scripts/build_report.py
```

For a benchmark-only run, use `java -Xms256m -Xmx3g -jar target/graph-benchmark.jar` after building. To produce a report for a new experiment, use the full reproduction script so the log, machine details and test totals are refreshed together. The report rejects a CSV whose SHA-256 differs from its analysis or environment record.

The full benchmark produces **50 data rows** in `results/results.csv`: five algorithms across five sizes (`1000`, `3162`, `10000`, `31623`, `100000`) and two densities (`m = 4n`, `m = 20n`). Each configuration generates three separate graph kinds: connected undirected graphs for MST, directed graphs for SCCs, and DAGs for topological sorting and shortest paths. DSU is measured through Kruskal, so it does not have a separate CSV row. The assignment's detailed ten-configuration, 50-row specification takes precedence over the inconsistent “four generated graphs” phrase in its deliverables list.

Every generator starts a fresh `Random(42)` and follows the assignment's exact shuffle, endpoint, and weight sequence; weights are in `[1, 1000]`, and parallel edges are retained. Each algorithm is measured five times on the same generated graph, with no separate warm-up runs, and `time_ms` is their median. Timing covers the entire `compute` call, including its internal preparation and allocation. Graph generation, correctness checks, distance summation, counter comparisons, and CSV writing occur outside the timed interval.

The benchmark checks every reference MST weight, SCC count, and DAG distance sum, compares both MST algorithms, validates topological orders, checks the required exact scan counts, and checks that repeated results/counters agree. A failed check aborts the run before replacing the CSV. Wall-clock timings remain sensitive to JIT compilation, garbage collection, hardware, and background load; counters are the main evidence.

## Counters and plots

The CSV columns are:

```text
algorithm,graph,n,m,time_ms,dsu_ops,edge_scans,comparisons,max_depth,result
```

| Field | What is counted |
| --- | --- |
| `dsu_ops` | Calls to `find` plus successful unions, recorded inside DSU and reported by Kruskal. This counts API work, not each parent-pointer traversal. |
| `edge_scans` | Edges examined by the principal traversal: Kruskal's sorted-edge loop, Prim's adjacency scans, both Kosaraju DFS passes, Kahn's outgoing-edge processing, or DAG relaxation. |
| `comparisons` | Actual weight comparator calls in Kruskal's sort and Prim's priority queue. |
| `max_depth` | Maximum occupancy of Kosaraju's explicit traversal stack. |
| `result` | MST/forest weight, SCC count, topological order size, or the sum of DAG distances. |

The required scan convention excludes preparatory indegree counting and graph reversal. DAG shortest paths also excludes its internal topological sort from its own `edge_scans`; its runtime includes these prerequisites. Consequently, Kosaraju reports exactly `2m`, Kahn and DAG relaxation exactly `m`, and Prim scans `2m` adjacency entries on the generated undirected graphs. These are deliberately scoped counters, not counts of every primitive instruction. Values can depend on implementation details such as sorting and heap tie handling; the program checks repeatability within a run.

The plotting script reads the measured CSV and produces:

- [Operations versus n](results/plots/operations-vs-n.png): algorithm work across both densities on logarithmic axes.
- [Scans per edge](results/plots/scans-per-edge.png): normalized traversal work.
- [Kruskal examined-edge share](results/plots/kruskal-examined-share.png): the fraction of sorted edges examined before the spanning tree is complete.

Kruskal's sorting cost is visible in `comparisons`; the logarithmic factor does not imply that its `edge_scans` curve must be superlinear. Fitted slopes and stable normalized ratios over five sizes are empirical consistency checks, not proofs of asymptotic complexity. See the report for exact operation definitions and measured values.

## Files and input format

| Location | Contents |
| --- | --- |
| `src/main/java/graph/` | Shared graph, weighted edges, file loading and graph reversal. |
| `src/main/java/algorithms/` | DSU and the five graph algorithms with result objects. |
| `src/main/java/metrics/` | Counters updated inside the algorithms. |
| `src/main/java/benchmark/` | Exact seeded generators and self-checking benchmark. |
| `src/test/java/` | JUnit 5 correctness, reference, fixture, and stack-safety tests. |
| `data/` | Small graphs for inspection and file-input tests. |
| `results/` | CSV, benchmark log, environment details, and plots. |
| `scripts/` | Experiment reproduction, analysis, and report generation. |
| `REPORT.md`, `output/pdf/REPORT.pdf` | Report in Markdown and printable PDF. |

An input file begins with `n m directed` or `n m undirected`, followed by exactly `m` lines containing `u v weight`. Vertices use zero-based indices:

```text
3 2 undirected
0 1 4
1 2 -2
```

The fixtures are [disconnected-undirected.txt](data/disconnected-undirected.txt), [cyclic-directed.txt](data/cyclic-directed.txt), and [dag-negative.txt](data/dag-negative.txt). Use `Graph.fromFile(Path)` to load them. The benchmark itself generates the prescribed large graphs in memory; it does not load the fixtures or store large generated edge lists.

## Scope and Git workflow

The required assignment scope is implemented. The optional Tarjan SCC and condensation/critical-path bonuses are not included.

The submission workflow uses `main`, feature branches `feature/dsu`, `feature/mst`, `feature/scc`, `feature/paths`, and `feature/metrics`, and release tag `v1.0`. Changes are grouped into meaningful commits for implementation, tests, benchmark methodology, and documentation. Inspect the actual history and refs with:

```bash
git log --graph --decorate --oneline --all
git branch --all
git tag --list
```

## AI-use acknowledgement

The student supplied the original graph and algorithm implementations. OpenAI Codex assisted with auditing the assignment requirements, existing code and input handling, strengthening tests, improving build and benchmark methodology, generating and interpreting the charts, preparing the report and reproduction scripts, and organizing the submission. This assistance included code and documentation changes. The student is responsible for reviewing the final work and explaining the algorithms, measurements, and conclusions at the defense.
