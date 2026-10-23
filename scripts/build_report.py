#!/usr/bin/env python3
"""Build the same five-page report as PDF and readable repository Markdown.

Run plot_results.py first. Numerical tables are calculated from results.csv;
test/environment metadata are read from the recorded benchmark environment.
"""
from __future__ import annotations

import hashlib
import html
import json
import math
import statistics
from pathlib import Path

import matplotlib
from reportlab import rl_config
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, Image, PageBreak,
)
from pypdf import PdfReader
from plot_results import read_results

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "results"
OUT = ROOT / "output" / "pdf" / "REPORT.pdf"
NAVY = colors.HexColor("#15344B")
TEAL = colors.HexColor("#147D92")
GRAY = colors.HexColor("#4D5C68")
LIGHT = colors.HexColor("#EDF3F6")
rl_config.invariant = 1


def series(rows, algorithm, density):
    return sorted((r for r in rows if r["algorithm"] == algorithm
                   and r["graph"].endswith("_" + density)), key=lambda r: r["n"])


def slope(rows, field):
    x = [math.log(r["n"]) for r in rows]
    y = [math.log(r[field]) for r in rows]
    xm, ym = statistics.mean(x), statistics.mean(y)
    b = sum((a-xm)*(v-ym) for a, v in zip(x, y)) / sum((a-xm)**2 for a in x)
    error = sum((v - (ym + b*(a-xm)))**2 for a, v in zip(x, y))
    total = sum((v-ym)**2 for v in y)
    return b, 1 - error/total


def span(values):
    return f"{min(values):.3f} to {max(values):.3f}"


def build():
    csv_path = RESULTS / "results.csv"
    rows = read_results(csv_path)
    digest = hashlib.sha256(csv_path.read_bytes()).hexdigest()
    analysis = json.loads((RESULTS / "analysis.json").read_text())
    env = json.loads((RESULTS / "environment.json").read_text())
    if analysis["csv_sha256"] != digest or env["csv_sha256"] != digest:
        raise ValueError("CSV, analysis and environment disagree; rerun scripts/reproduce.sh")
    tests = env["tests"]["tests"]
    if any(env["tests"][k] for k in ("failures", "errors", "skipped")):
        raise ValueError("Report requires a fully passing, unskipped test suite")
    fonts = Path(matplotlib.get_data_path()) / "fonts" / "ttf"
    pdfmetrics.registerFont(TTFont("Report", str(fonts / "DejaVuSans.ttf")))
    pdfmetrics.registerFont(TTFont("ReportBold", str(fonts / "DejaVuSans-Bold.ttf")))
    body = ParagraphStyle("body", fontName="Report", fontSize=8.7, leading=12.3,
                          textColor=NAVY, spaceAfter=6)
    small = ParagraphStyle("small", parent=body, fontSize=7.5, leading=10, spaceAfter=0)
    heading = ParagraphStyle("heading", parent=body, fontName="ReportBold", fontSize=13,
                             leading=17, textColor=TEAL, spaceBefore=7, spaceAfter=8)
    title = ParagraphStyle("title", parent=heading, fontSize=24, leading=29, textColor=NAVY)
    caption = ParagraphStyle("caption", parent=body, fontSize=7.6, leading=10,
                             textColor=GRAY, spaceAfter=7)
    story, md = [], []

    def p(text, style=body):
        story.append(Paragraph(html.escape(text), style))
        md.append(text + "\n")

    def h(text):
        story.append(Paragraph(html.escape(text), heading))
        md.append("## " + text + "\n")

    def table(headers, data, widths):
        all_rows = [[Paragraph(html.escape(str(c)), small) for c in row]
                    for row in [headers, *data]]
        t = Table(all_rows, colWidths=widths, repeatRows=1, hAlign="LEFT")
        t.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), LIGHT),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LINEBELOW", (0, 0), (-1, 0), 0.7, TEAL),
            ("LINEBELOW", (0, 1), (-1, -1), 0.25, colors.HexColor("#DCE4EA")),
            ("LEFTPADDING", (0, 0), (-1, -1), 6),
            ("RIGHTPADDING", (0, 0), (-1, -1), 6),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ]))
        story.extend([t, Spacer(1, 7)])
        md.extend(["| " + " | ".join(headers) + " |", "| " + " | ".join("---" for _ in headers) + " |"])
        md.extend("| " + " | ".join(str(x).replace("|", "\\|") for x in row) + " |" for row in data)
        md.append("")

    def figure(filename, text, max_height=220):
        path = RESULTS / "plots" / filename
        im = Image(str(path))
        scale = min(499 / im.imageWidth, max_height / im.imageHeight)
        im.drawWidth = im.imageWidth * scale
        im.drawHeight = im.imageHeight * scale
        im.hAlign = "CENTER"
        story.append(im)
        story.append(Paragraph(html.escape(text), caption))
        md.extend([f"![{text}](results/plots/{filename})", ""])

    def newpage():
        story.append(PageBreak())
        md.extend(["", "<!-- page break in PDF -->", ""])

    p("City Network & Job Scheduler", title)
    p("Design and Analysis of Algorithms · Assignment 3", heading)
    p("Adilzhan Kuandykov | SE-2526 | 23 October 2026")
    p("Repository: https://github.com/adilzhandev/AITU-DAA-A3 | Release: v1.0", caption)
    h("1. Implementation and experimental design")
    p("One adjacency-list Graph supports directed and undirected weighted multigraphs, including loops, parallel edges, isolated vertices and disconnected components. DSU uses union by size and iterative path compression. Kruskal and lazy-heap Prim return minimum spanning forests; Kosaraju uses an explicit stack; Kahn detects cycles; DAG shortest paths start at every original zero-indegree vertex and retain parents.")
    p("The exact Random(42) generator is restarted for each graph: n = 1,000, 3,162, 10,000, 31,623, 100,000; m = 4n or 20n. Each size/density supplies an undirected graph, an unrestricted directed graph and a DAG. The 50 CSV rows contain five runs per algorithm/graph, with median compute time. Generation and validation are outside the timer; there is no separate warm-up phase. Every run's result and counters are checked for determinism, both MST results agree, all DAG orders are validated and all supplied reference values pass.")
    h("Complexity of the submitted implementations")
    table(["Operation", "Time", "Extra space", "Reason"], [
        ["DSU find / union", "O(α(V)) amortized", "O(1) per call; O(V) structure", "Size balancing + compression; makeSet costs O(V)."],
        ["Kruskal", "O(V + E log(E+1) + Eα(V))", "O(V+E)", "Sort a copied edge list, then DSU checks; stop at V-1 selected edges."],
        ["Prim (lazy heap)", "O(V + E log(E+1))", "O(V+E)", "Scan adjacency lists; heap stores up to O(E) candidate edges."],
        ["Kosaraju", "O(V+E)", "O(V+E)", "Two iterative DFS passes and an explicit transposed graph."],
        ["Kahn", "O(V+E)", "O(V)", "In-degrees and queue; each vertex leaves once and each arc is removed once."],
        ["DAG shortest paths", "O(V+E)", "O(V)", "Topological ordering plus one relaxation pass; distance and parent arrays."],
    ], [100, 121, 95, 183])
    p("V=n and E=m count original edges; an undirected edge occupies two adjacency entries. Space excludes the input graph and includes returned arrays/edges. For simple connected graphs log E = O(log V), but the implemented lazy Prim supports parallel edges, so O(E log E) is the precise heap bound. These linear-density inputs are both E=Θ(V); 'dense' here means 20n, not Θ(V²).", caption)
    h("What the counters measure")
    p("dsuOps counts find calls plus successful unions, not individual parent-pointer hops. comparisons counts actual weight-comparator calls in sorting or the heap. edgeScans counts the main phase: Kruskal candidates, Prim adjacency visits, two Kosaraju DFS passes, Kahn outgoing-edge removals, or DAG relaxations. Transposition, in-degree initialization and the DAG algorithm's prerequisite Kahn pass are deliberately excluded from this phase counter. maxDepth is peak simultaneous explicit-stack entries, not an additive work count.")

    newpage()
    h("2. Correctness and verification")
    p("Cut property. Let A be an acyclic partial solution that is contained in some minimum spanning tree T. Consider a cut that no edge of A crosses, and let e be a lightest edge across that cut. If e is already in T, adding it to A is safe. Otherwise, adding e to T creates a cycle that contains another crossing edge f. Since w(e) ≤ w(f), replacing f by e preserves a spanning tree and cannot increase its weight. Kruskal applies this argument between DSU components, while Prim applies it between the visited vertices and the rest of the current connected component. Repeating the safe choice gives an MST, or independently one minimum tree per component for a disconnected graph.")
    p("Topological induction. Give every original zero-indegree vertex distance zero, equivalently adding a virtual source with zero-weight arcs to those vertices. Every vertex of a finite DAG is reachable from such a source. Assume that all vertices before u in a topological order already have their correct distances. Every incoming edge of u comes from one of those earlier vertices, so all possible final edges of a path to u have already been relaxed. The stored minimum is therefore the true shortest distance to u. Relaxing all outgoing edges of u maintains the induction, and parent updates preserve a path realizing each finite distance. The proof uses edge direction in the order rather than nonnegative weights, so negative edges are safe in a DAG, whereas general graphs need another method such as Bellman-Ford and reachable negative cycles prevent finite shortest-walk distances.")
    p(f"Verification: {tests} JUnit tests pass with no failures, errors or skips. Coverage includes 1,000 random DSU unions against a label reference, 100 connected MST cross-checks, 80 exhaustive small forest oracles, 100 SCC graphs with up to 200 vertices against BFS reachability, 100 random DAGs against Bellman-Ford including negative weights and restored paths, cycle rejection and file parsing. The 100,000-vertex SCC path finishes with maxDepth=100,000. Three data/ fixtures exercise actual file loading, disconnected forests, SCC cycles, parallel edges, self-loops, isolated vertices and negative DAG weights.")
    h("Exact reference results reproduced")
    refs = []
    for n in sorted({r["n"] for r in rows}):
        for d in ("sparse", "dense"):
            get = lambda a: next(r for r in series(rows, a, d) if r["n"] == n)
            refs.append([f"{n:,}", f"{get('Kruskal')['m']:,}", f"{get('Kruskal')['result']:,}",
                         str(get('Kosaraju')['result']), f"{get('DagShortestPath')['result']:,}"])
    table(["n", "m", "MST weight (both)", "SCCs", "Sum of DAG distances"], refs, [72, 84, 116, 59, 168])
    p("Kahn returns n vertices in all ten cases. Reference results are from Assignment 3, section 7; all 50 rows and per-configuration checks are retained in results/results.csv and results/benchmark.log.", caption)

    newpage()
    h("3. Operations and normalized complexity")
    figure("operations-vs-n.png", "Figure 1. Selected operation total = edgeScans + comparisons + dsuOps; maxDepth is excluded. Both axes are logarithmic. Kahn and DAG paths overlap exactly.", 240)
    p("The total combines distinct recorded events as a work proxy; it is neither an instruction count nor a prediction of elapsed time. MST work is dominated by weight comparisons, whereas traversal totals here consist only of counted edge visits. Ratios below use all five sizes independently within each density; a narrow range is evidence consistent with a formula, not a proof of an asymptotic bound.")
    ratio_rows = []
    specs = [
        ("Kruskal", "comparisons / (m log₂m)", lambda r: r["comparisons"]/(r["m"]*math.log2(r["m"]))),
        ("Kruskal", "dsuOps / m", lambda r: r["dsu_ops"]/r["m"]),
        ("Prim", "comparisons / (m log₂m)", lambda r: r["comparisons"]/(r["m"]*math.log2(r["m"]))),
        ("Prim", "edgeScans / (n+m)", lambda r: r["edge_scans"]/(r["n"]+r["m"])),
        ("Kosaraju", "edgeScans / (n+m)", lambda r: r["edge_scans"]/(r["n"]+r["m"])),
        ("Kahn", "edgeScans / (n+m)", lambda r: r["edge_scans"]/(r["n"]+r["m"])),
        ("DAG paths", "edgeScans / (n+m)", lambda r: r["edge_scans"]/(r["n"]+r["m"])),
    ]
    for alg, formula, fn in specs:
        a = "DagShortestPath" if alg == "DAG paths" else alg
        ratio_rows.append([alg, formula, span([fn(r) for r in series(rows,a,"sparse")]), span([fn(r) for r in series(rows,a,"dense")])])
    table(["Algorithm", "Normalized counter", "Sparse min to max", "Dense min to max"], ratio_rows, [79, 184, 118, 118])
    p("Traversal ratios are exactly flat because m=cn: 2m/(n+m)=2c/(1+c) and m/(n+m)=c/(1+c). Prim's heap comparisons grow at the expected near m log m scale; Kruskal's comparison ratio is not exactly constant, partly because only 1,000 distinct weights occur and sorting adapts to the input. Its DSU ratio also varies as early stopping examines a changing fraction of edges. For k examined candidates and C components, dsuOps = 2k + n - C; thus dsuOps/k remains bounded between 2 and 3. Counting calls cannot empirically establish α(n): that bound comes from union-by-size plus path compression.")

    newpage()
    h("4. Edge visits and Kruskal's early stop")
    figure("scans-per-edge.png", "Figure 2. Original edges m are the denominator. Prim and Kosaraju are exactly 2; Kahn and DAG relaxation are exactly 1. Coincident curves are labelled.", 210)
    p("Prim's factor two comes from the undirected adjacency representation; Kosaraju's factor two comes from its two directed DFS passes. Their equal counts do not mean equal tasks, memory usage or elapsed time. Kahn and DAG paths also have different preprocessing although their reported phase scans coincide.")
    figure("kruskal-examined-share.png", "Figure 3. Kruskal stops its DSU scan after choosing n-1 edges, but first sorts all m input edges.", 210)
    ks, kd = series(rows,"Kruskal","sparse"), series(rows,"Kruskal","dense")
    pct = lambda r: 100*r["edge_scans"]/r["m"]
    p(f"The sparse examined share rises from {pct(ks[0]):.1f}% to {pct(ks[-1]):.1f}%; with fewer alternative connections it approaches the whole edge list. Dense shares range from {min(map(pct,kd)):.1f}% to {max(map(pct,kd)):.1f}%, so many expensive candidates never reach DSU. This saves scanning and union/find calls, not sorting. Prim still visits all 2m adjacency entries and manages its lazy heap. The measured comparison counts favor Kruskal on these generated inputs; these curves alone do not imply a universal winner.")

    newpage()
    h("5. Slopes, algorithm choice and timing")
    slope_rows = []
    for alg, field, label in [("Prim","edge_scans","Prim scans"), ("Kosaraju","edge_scans","Kosaraju scans"),
                              ("Kahn","edge_scans","Kahn / DAG scans"), ("Kruskal","edge_scans","Kruskal scans"),
                              ("Kruskal","comparisons","Kruskal sort comparisons"), ("Prim","comparisons","Prim heap comparisons")]:
        values = [slope(series(rows,alg,d),field) for d in ("sparse","dense")]
        slope_rows.append([label, f"{values[0][0]:.3f}", f"{values[1][0]:.3f}", f"{min(v[1] for v in values):.4f}"])
    table(["Curve", "Sparse slope", "Dense slope", "Minimum R²"], slope_rows, [210, 94, 94, 101])
    p("OLS fits log(counter) = a + b log(n) on the five measured sizes. Traversal slopes are exactly 1.000. Kruskal's scans are slightly superlinear here because its examined fraction changes, not because edgeScans includes sorting. The E log E term belongs to the comparison phase; the assignment's slope wording conflates these counters. Finite slopes and R² describe this seed and size range, not a proof or a general performance ranking.")
    table(["Situation", "Choice and reason"], [
        ["Sparse undirected graph; edge list", "Kruskal: direct sort plus DSU, straightforward forest handling."],
        ["Undirected graph; adjacency list", "Prim grows a component naturally. This lazy heap uses O(E) space; compare counts before preferring it."],
        ["Truly dense simple graph, E≈V²", "An O(V²) array/matrix Prim variant can avoid heap overhead; that variant is not implemented here."],
        ["Directed graph may have cycles", "Kosaraju groups mutual reachability/deadlocks; Kahn detects cycles when only scheduling validity is needed."],
        ["Acyclic jobs / weighted pipeline", "Kahn for a schedule; topological relaxation for paths, including negative weights."],
        ["General weighted directed graph", "DAG relaxation is invalid if cyclic. Dijkstra needs nonnegative weights; Bellman-Ford supports negative edges and detects reachable negative cycles."],
    ], [183, 316])
    largest = {a: series(rows,a,"dense")[-1]["time_ms"] for a in ("Kruskal","Prim","Kosaraju","Kahn","DagShortestPath")}
    runtime = env["java"].splitlines()[0]
    os_name = env.get("os_pretty_name", env["platform"])
    hardware = f"{runtime}; {os_name}; {env['processor']}; {' '.join(env['java_options'])}"
    p("Timing. On the largest dense graph, median times in ms were " + ", ".join(f"{a} {t:,.3f}" for a,t in largest.items()) + f". Environment: {hardware}. Five-run medians reduce outliers but do not remove JVM compilation, allocation/GC, background load or hardware effects; the small inputs are especially noisy. Counters and reference checks are the primary evidence. Machine details and test totals are recorded in results/environment.json.")
    p("Reproduction and provenance. After installing scripts/requirements.txt, run bash scripts/reproduce.sh to test, benchmark and rebuild the three plots plus this report. The Java tests and benchmark are the correctness evidence; scripts/plot_results.py validates the CSV and stores numerical fits in results/analysis.json. Main, five feature branches and tag v1.0 identify the submitted release. Source: Taubakabyl Nurlybek, DAA Assignment 3 - Graphs (supplied specification), sections 1-10. AI acknowledgement: the student supplied the initial Java implementations; OpenAI Codex assisted with auditing, additional tests, benchmark timing isolation, plots, report and release preparation.", caption)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    def furniture(canvas, doc):
        w, height = A4
        canvas.setStrokeColor(TEAL)
        canvas.setLineWidth(1)
        canvas.line(48, height-34, w-48, height-34)
        canvas.setFillColor(GRAY)
        canvas.setFont("Report", 7)
        canvas.drawString(48, 27, "DAA / ASSIGNMENT 3     ADILZHAN KUANDYKOV · SE-2526")
        canvas.drawRightString(w-48, 27, f"{doc.page} / 5")
    doc = SimpleDocTemplate(str(OUT), pagesize=A4, rightMargin=48, leftMargin=48,
                            topMargin=45, bottomMargin=43,
                            title="DAA Assignment 3 - City Network & Job Scheduler",
                            author="Adilzhan Kuandykov", pageCompression=1)
    doc.build(story, onFirstPage=furniture, onLaterPages=furniture)
    pages = len(PdfReader(OUT).pages)
    if pages != 5:
        raise RuntimeError(f"Expected exactly 5 report pages, got {pages}; adjust layout")
    (ROOT / "REPORT.md").write_text("# Assignment 3 report\n\n" + "\n".join(md), encoding="utf-8")
    print(f"Built {OUT.relative_to(ROOT)} ({pages} pages) and REPORT.md")


if __name__ == "__main__":
    build()
