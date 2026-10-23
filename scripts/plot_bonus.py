#!/usr/bin/env python3
"""Validate bonus benchmark evidence and draw the SCC/critical-path figures.

Run from any directory: python /path/to/project/scripts/plot_bonus.py
The CSV contains measured data; the scheduler diagram is independently checked
against data/bonus-scheduler.txt before any figures or JSON are written.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import re

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch

from plot_results import (
    DENSITIES, FIELDS, REFERENCE, SIZES, configure_style, fit_log_log, require,
    setup_axis,
)


ROOT = Path(__file__).resolve().parents[1]
ALGORITHMS = ("Kosaraju", "Tarjan", "Condensation", "DagLongestPath")
SCC_STYLES = {
    "Kosaraju": dict(color="#0072B2", marker="s", linestyle="--", linewidth=2.0),
    "Tarjan": dict(color="#009E73", marker="o", linestyle="-", linewidth=2.4),
}


def read_bonus_results(path: Path) -> list[dict]:
    """Return validated CSV records plus density, original_n and original_m.

    Full SCC partitions and longest-path witnesses are checked by the Java
    benchmark before output. This reader checks the complete ten-configuration
    matrix, assignment SCC counts, phase counters and cross-algorithm joins.
    """
    rows = []
    by_key = {}
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        require(tuple(reader.fieldnames or ()) == FIELDS, "Bonus CSV columns do not match the assignment schema")
        for line_number, raw in enumerate(reader, start=2):
            require(set(raw) == set(FIELDS) and all(raw.values()), f"Malformed bonus CSV row {line_number}")
            row = {key: raw[key] for key in ("algorithm", "graph")}
            try:
                row.update({key: int(raw[key]) for key in FIELDS[2:] if key != "time_ms"})
                row["time_ms"] = float(raw["time_ms"])
            except ValueError as error:
                raise ValueError(f"Invalid numeric value on bonus CSV row {line_number}") from error
            algorithm = row["algorithm"]
            require(algorithm in ALGORITHMS, f"Unknown bonus algorithm: {algorithm}")
            if algorithm == "DagLongestPath":
                match = re.fullmatch(r"condensation_(sparse|dense)_([0-9]+)", row["graph"])
                require(match is not None, f"Missing original graph size on bonus CSV row {line_number}")
                density, original_n = match.group(1), int(match.group(2))
            else:
                match = re.fullmatch(r"directed_(sparse|dense)", row["graph"])
                require(match is not None, f"Wrong input graph for {algorithm}")
                density, original_n = match.group(1), row["n"]
            require(original_n in SIZES, f"Unexpected original graph size on bonus CSV row {line_number}")
            original_m = original_n * DENSITIES[density]
            row.update(density=density, original_n=original_n, original_m=original_m)
            key = algorithm, density, original_n
            require(key not in by_key, f"Duplicate bonus benchmark row: {key}")
            require(math.isfinite(row["time_ms"]) and row["time_ms"] >= 0, f"Invalid time for {key}")
            require(row["n"] >= 1 and row["m"] >= 0, f"Invalid graph dimensions for {key}")
            require(all(row[name] >= 0 for name in ("dsu_ops", "edge_scans", "comparisons", "max_depth")),
                    f"Negative counter for {key}")
            require(row["dsu_ops"] == row["comparisons"] == 0, f"Unexpected DSU/comparator count for {key}")
            expected_scans = (2 if algorithm == "Kosaraju" else 1) * row["m"]
            require(row["edge_scans"] == expected_scans, f"Exact edge-scan identity failed for {key}")
            components = REFERENCE[original_n, density][1]
            if algorithm != "DagLongestPath":
                require((row["n"], row["m"]) == (original_n, original_m),
                        f"Wrong original dimensions for {key}")
            if algorithm in ("Kosaraju", "Tarjan"):
                require(row["result"] == components, f"Assignment SCC reference mismatch for {key}")
                require(1 <= row["max_depth"] <= row["n"], f"Invalid DFS stack depth for {key}")
            else:
                require(row["max_depth"] == 0, f"Unexpected DFS stack counter for {key}")
            if algorithm == "Condensation":
                require(0 <= row["result"] <= min(original_m, components * (components - 1) // 2),
                        f"Invalid number of condensed edges for {key}")
            if algorithm == "DagLongestPath":
                require(row["n"] == components, f"Condensation vertex count differs from SCC reference for {key}")
                require(row["m"] <= min(original_m, components * (components - 1) // 2),
                        f"Too many edges for a simple condensation DAG: {key}")
                # The seed-42 generator uses positive integer weights in [1, 1000].
                require(0 <= row["result"] <= 1000 * (components - 1),
                        f"Critical weight is outside generated-graph bounds for {key}")
                require((row["m"] == 0) == (row["result"] == 0),
                        f"Positive-weight critical path is inconsistent with edge count for {key}")
            by_key[key] = row
            rows.append(row)

    expected_keys = {(algorithm, density, n) for algorithm in ALGORITHMS
                     for density in DENSITIES for n in SIZES}
    require(len(rows) == 40 and set(by_key) == expected_keys,
            "Expected all 40 bonus algorithm/size/density rows")
    for n in SIZES:
        for density in DENSITIES:
            kosaraju = by_key["Kosaraju", density, n]
            tarjan = by_key["Tarjan", density, n]
            condensation = by_key["Condensation", density, n]
            longest = by_key["DagLongestPath", density, n]
            require(kosaraju["result"] == tarjan["result"] == longest["n"],
                    f"SCC/condensation component counts differ for {(n, density)}")
            require(condensation["result"] == longest["m"],
                    f"Condensation/longest-path edge counts differ for {(n, density)}")
    return rows


def verify_scheduler_fixture(path: Path) -> dict:
    """Independently derive small-fixture SCCs, max-weight edges and all paths."""
    lines = path.read_text(encoding="utf-8").splitlines()
    require(bool(lines), "Empty scheduler fixture")
    header = lines[0].split()
    require(len(header) == 3 and header[2] == "directed", "Scheduler fixture must be directed")
    n, m = map(int, header[:2])
    require(n == 9, "Scheduler diagram requires the verified nine-vertex fixture")
    edges = [tuple(map(int, line.split())) for line in lines[1:] if line.strip()]
    require(len(edges) == m and all(len(edge) == 3 for edge in edges), "Invalid scheduler fixture edges")
    require(all(0 <= u < n and 0 <= v < n for u, v, _ in edges), "Invalid scheduler fixture vertex")
    reachable = [[u == v for v in range(n)] for u in range(n)]
    for u, v, _ in edges:
        reachable[u][v] = True
    for middle in range(n):
        for u in range(n):
            for v in range(n):
                reachable[u][v] |= reachable[u][middle] and reachable[middle][v]

    groups = []
    assigned = set()
    for vertex in range(n):
        if vertex not in assigned:
            group = [other for other in range(n) if reachable[vertex][other] and reachable[other][vertex]]
            groups.append(group)
            assigned.update(group)
    require(groups == [[0, 1, 2], [3, 4], [5], [6, 7], [8]], "Scheduler fixture SCC partition changed")
    membership = {vertex: chr(65 + index) for index, group in enumerate(groups) for vertex in group}
    transitions = {}
    for u, v, weight in edges:
        a, b = membership[u], membership[v]
        if a != b:
            transitions[a, b] = max(transitions.get((a, b), weight), weight)
    expected = {("A", "B"): 7, ("A", "C"): 3, ("B", "C"): 5,
                ("B", "D"): 6, ("C", "D"): 8}
    require(transitions == expected, "Scheduler fixture maximum inter-component weights changed")
    labels = [chr(65 + index) for index in range(len(groups))]
    sources = [label for label in labels if all(b != label for _, b in transitions)]
    paths = []

    def enumerate_paths(path_labels: list[str], weight: int) -> None:
        outgoing = [(b, w) for (a, b), w in transitions.items() if a == path_labels[-1]]
        if not outgoing:
            paths.append((weight, path_labels))
        for target, edge_weight in outgoing:
            require(target not in path_labels, "Scheduler condensation contains a cycle")
            enumerate_paths(path_labels + [target], weight + edge_weight)

    for source in sources:
        enumerate_paths([source], 0)
    critical_weight, critical_path = max(paths, key=lambda item: item[0])
    require(critical_weight == 20 and critical_path == ["A", "B", "C", "D"],
            "Scheduler fixture critical path changed")
    return {
        "path": str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "components": dict(zip(labels, groups)),
        "edges": [{"from": a, "to": b, "weight": weight} for (a, b), weight in transitions.items()],
        "critical_path": critical_path,
        "critical_weight": critical_weight,
        "weight_policy": "Maximum weight per ordered SCC pair; internal SCC edges contribute zero.",
        "verification": "Independent transitive closure, edge aggregation, exhaustive source-to-sink paths.",
    }


def analyze_bonus(rows: list[dict], csv_path: Path, fixture: dict) -> dict:
    analysis = {
        "schema_version": 1,
        "csv_sha256": hashlib.sha256(csv_path.read_bytes()).hexdigest(),
        "row_count": len(rows),
        "sizes": list(SIZES),
        "density_multipliers": DENSITIES,
        "counter_scope": "Kosaraju counts two DFS passes (2m); Tarjan one DFS pass (m). Condensation counts only contraction (m), excluding its Tarjan call. DAG longest paths count relaxation edges, excluding topological sorting and indegree setup.",
        "fit_definition": "OLS on log10(edge_scans) versus log10(original_n), all five sizes.",
        "series": {},
        "comparisons": [],
        "scheduler_fixture": fixture,
    }
    for algorithm in ("Kosaraju", "Tarjan"):
        analysis["series"][algorithm] = {}
        for density in DENSITIES:
            points = sorted((row for row in rows if row["algorithm"] == algorithm
                             and row["density"] == density), key=lambda row: row["original_n"])
            analysis["series"][algorithm][density] = {
                "points": [{key: value for key, value in point.items()
                            if key not in ("algorithm", "graph", "density")} for point in points],
                "fits": {"edge_scans": fit_log_log([point["original_n"] for point in points],
                                                   [point["edge_scans"] for point in points])},
            }
    by_key = {(row["algorithm"], row["density"], row["original_n"]): row for row in rows}
    for n in SIZES:
        for density in DENSITIES:
            kosaraju = by_key["Kosaraju", density, n]
            tarjan = by_key["Tarjan", density, n]
            longest = by_key["DagLongestPath", density, n]
            analysis["comparisons"].append({
                "original_n": n,
                "original_m": kosaraju["m"],
                "density": density,
                "components": tarjan["result"],
                "condensed_edges": longest["m"],
                "critical_weight": longest["result"],
                "kosaraju_scans": kosaraju["edge_scans"],
                "tarjan_scans": tarjan["edge_scans"],
                "scan_saving_fraction": 1 - tarjan["edge_scans"] / kosaraju["edge_scans"],
                "kosaraju_time_ms": kosaraju["time_ms"],
                "tarjan_time_ms": tarjan["time_ms"],
                "tarjan_over_kosaraju_time": (tarjan["time_ms"] / kosaraju["time_ms"]
                                               if kosaraju["time_ms"] > 0 else None),
            })
    return analysis


def scc_scans_plot(analysis: dict, output: Path) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(12, 3.8), sharey=True)
    fig.subplots_adjust(left=0.075, right=0.98, bottom=0.235, top=0.715, wspace=0.13)
    for ax, density in zip(axes, DENSITIES):
        setup_axis(ax)
        ax.set_yscale("log")
        ax.set_ylim(2_000, 6_000_000)
        for algorithm, style in SCC_STYLES.items():
            series = analysis["series"][algorithm][density]
            ax.plot([point["n"] for point in series["points"]],
                    [point["edge_scans"] for point in series["points"]],
                    label=f"{algorithm}: {'2m' if algorithm == 'Kosaraju' else 'm'}",
                    markersize=6.5, markerfacecolor="white", markeredgewidth=1.5, **style)
        ax.set_title(f"{density.capitalize()} · m = {DENSITIES[density]}n", pad=10)
        ax.text(0.025, 0.95, "50% fewer DFS edge scans", transform=ax.transAxes,
                va="top", color="#006A50", fontsize=10.2, fontweight="bold")
        ax.legend(loc="lower right", fontsize=9.6, handlelength=2.3, labelspacing=0.35)
    axes[0].set_ylabel("DFS edge scans", labelpad=8)
    fig.suptitle("Tarjan halves the counted SCC edge scans", x=0.075, y=0.985,
                 ha="left", fontsize=18.5, fontweight="bold")
    slopes = [analysis["series"][algorithm][density]["fits"]["edge_scans"]["slope"]
              for algorithm in SCC_STYLES for density in DENSITIES]
    fig.text(0.075, 0.875,
             f"Log–log measurements on all ten directed graphs; all four fitted slopes = {slopes[0]:.3f}.",
             fontsize=11.1, color="#546276")
    require(all(abs(slope - 1.0) < 1e-9 for slope in slopes), "SCC scan slopes should be exactly linear")
    fig.text(0.075, 0.025,
             "DFS phase counters only: transpose construction is excluded; a 50% scan saving is not a runtime claim.",
             fontsize=10.3, color="#546276")
    fig.savefig(output / "scc-scan-comparison.png")
    plt.close(fig)


def condensation_plot(fixture: dict, output: Path) -> None:
    fig, ax = plt.subplots(figsize=(12, 3.2))
    fig.subplots_adjust(left=0.045, right=0.985, bottom=0.14, top=0.80)
    ax.set_xlim(0, 12)
    ax.set_ylim(-0.18, 2.65)
    ax.axis("off")
    positions = {"A": 1.0, "B": 3.5, "C": 6.0, "D": 8.5, "E": 11.0}
    y = 1.18
    critical = set(zip(fixture["critical_path"], fixture["critical_path"][1:]))
    for edge in fixture["edges"]:
        source, target, weight = edge["from"], edge["to"], edge["weight"]
        x1, x2 = positions[source], positions[target]
        if (source, target) in critical:
            arrow = FancyArrowPatch((x1 + 0.71, y), (x2 - 0.71, y),
                                    arrowstyle="-|>", mutation_scale=15, linewidth=2.5,
                                    color="#007B69", zorder=1)
            label_y = y + 0.18
        else:
            upper = source == "A"
            offset = 0.39 if upper else -0.39
            rad = -0.25 if upper else 0.25
            arrow = FancyArrowPatch((x1, y + offset), (x2, y + offset),
                                    connectionstyle=f"arc3,rad={rad}", arrowstyle="-|>",
                                    mutation_scale=13, linewidth=1.5, color="#7B8798", zorder=1)
            label_y = y + offset + ((x2 - x1) * 0.125) * (1 if upper else -1)
        ax.add_patch(arrow)
        ax.text((x1 + x2) / 2, label_y, str(weight), ha="center", va="center",
                fontsize=12, fontweight="bold", color="#006A59" if (source, target) in critical else "#59677A",
                bbox=dict(facecolor="white", edgecolor="none", pad=2), zorder=3)

    for label, x in positions.items():
        selected = label in fixture["critical_path"]
        box = FancyBboxPatch((x - 0.67, y - 0.36), 1.34, 0.72,
                             boxstyle="round,pad=0.035,rounding_size=0.08",
                             facecolor="#E8F5F1" if selected else "#F1F3F6",
                             edgecolor="#007B69" if selected else "#A6B0BE",
                             linewidth=1.4, linestyle="-" if selected else "--", zorder=2)
        ax.add_patch(box)
        members = ", ".join(map(str, fixture["components"][label]))
        ax.text(x, y + 0.11, label, ha="center", va="center", fontsize=13.2, fontweight="bold")
        ax.text(x, y - 0.16, "{" + members + "}", ha="center", va="center", fontsize=10.4,
                color="#435266")
    ax.text(positions["E"], y - 0.61, "isolated", ha="center", va="center", fontsize=10, color="#69778A")
    fig.suptitle("Condensation turns the scheduler into a DAG", x=0.075, y=0.985,
                 ha="left", fontsize=18.5, fontweight="bold")
    fig.text(0.075, 0.855, "Verified fixture: SCC members inside boxes; edges retain the maximum parallel transition weight.",
             fontsize=10.8, color="#546276")
    fig.text(0.075, 0.035, "Critical path A → B → C → D: 7 + 5 + 8 = 20. Internal SCC edges contribute zero.",
             fontsize=11.2, color="#006A59", fontweight="bold")
    fig.savefig(output / "condensation-critical-path.png")
    plt.close(fig)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv", type=Path, default=ROOT / "results/bonus.csv")
    parser.add_argument("--fixture", type=Path, default=ROOT / "data/bonus-scheduler.txt")
    parser.add_argument("--output", type=Path, default=ROOT / "results/plots", help="PNG output directory")
    parser.add_argument("--analysis", type=Path, default=None,
                        help="JSON path (default: output parent / bonus-analysis.json)")
    args = parser.parse_args()
    try:
        rows = read_bonus_results(args.csv)
        fixture = verify_scheduler_fixture(args.fixture.resolve())
        analysis = analyze_bonus(rows, args.csv, fixture)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    args.output.mkdir(parents=True, exist_ok=True)
    analysis_path = args.analysis or args.output.parent / "bonus-analysis.json"
    analysis_path.parent.mkdir(parents=True, exist_ok=True)
    configure_style()
    scc_scans_plot(analysis, args.output)
    condensation_plot(fixture, args.output)
    analysis_path.write_text(json.dumps(analysis, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(f"Validated {len(rows)} bonus rows, all SCC references and the scheduler fixture.")
    print(f"Saved two bonus plots to {args.output.resolve()}")
    print(f"Saved bonus comparison evidence to {analysis_path.resolve()}")


if __name__ == "__main__":
    main()
