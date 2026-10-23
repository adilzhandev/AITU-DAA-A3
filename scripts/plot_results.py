#!/usr/bin/env python3
"""Validate the full benchmark, draw three figures, and export report statistics.

Run from any directory: python /path/to/project/scripts/plot_results.py
Requires matplotlib and numpy. No benchmark values are synthesized or adjusted.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import FixedLocator, FuncFormatter, NullFormatter, PercentFormatter
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
SIZES = (1_000, 3_162, 10_000, 31_623, 100_000)
DENSITIES = {"sparse": 4, "dense": 20}
KINDS = {
    "Kruskal": "undirected",
    "Prim": "undirected",
    "Kosaraju": "directed",
    "Kahn": "dag",
    "DagShortestPath": "dag",
}
FIELDS = (
    "algorithm", "graph", "n", "m", "time_ms", "dsu_ops", "edge_scans",
    "comparisons", "max_depth", "result",
)
# Assignment reference table: (MST weight, SCC count, sum of DAG distances).
REFERENCE = {
    (1_000, "sparse"): (147_970, 27, 396_274),
    (1_000, "dense"): (30_363, 1, 252_411),
    (3_162, "sparse"): (468_911, 137, 1_271_911),
    (3_162, "dense"): (97_589, 1, 723_157),
    (10_000, "sparse"): (1_486_139, 373, 3_941_828),
    (10_000, "dense"): (309_550, 1, 2_381_342),
    (31_623, "sparse"): (4_715_515, 1_188, 12_551_891),
    (31_623, "dense"): (968_517, 1, 7_376_612),
    (100_000, "sparse"): (15_012_094, 3_923, 40_096_539),
    (100_000, "dense"): (3_056_681, 1, 23_512_087),
}
STYLES = {
    "Kruskal": dict(color="#0072B2", marker="s", linestyle="-", linewidth=2.1),
    "Prim": dict(color="#D55E00", marker="o", linestyle="-", linewidth=2.5),
    "Kosaraju": dict(color="#009E73", marker="D", linestyle="--", linewidth=1.9),
    "Kahn": dict(color="#8B5A96", marker="^", linestyle="-", linewidth=2.5),
    "DagShortestPath": dict(color="#B67800", marker="x", linestyle=":", linewidth=2.0),
}
LABELS = {**{name: name for name in KINDS}, "DagShortestPath": "DAG shortest paths"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_results(path: Path) -> list[dict]:
    """Reject incomplete, mismatched, or invalid evidence before drawing it."""
    rows = []
    seen = set()
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        require(tuple(reader.fieldnames or ()) == FIELDS, "CSV columns do not match the assignment schema")
        for line_number, raw in enumerate(reader, start=2):
            require(set(raw) == set(FIELDS) and all(raw.values()), f"Malformed CSV row {line_number}")
            row = {key: raw[key] for key in ("algorithm", "graph")}
            try:
                row.update({key: int(raw[key]) for key in FIELDS[2:] if key != "time_ms"})
                row["time_ms"] = float(raw["time_ms"])
            except ValueError as error:
                raise ValueError(f"Invalid numeric value on CSV row {line_number}") from error
            algorithm = row["algorithm"]
            require(algorithm in KINDS, f"Unknown algorithm on row {line_number}: {algorithm}")
            density = row["graph"].rsplit("_", 1)[-1]
            require(density in DENSITIES, f"Unknown density on row {line_number}")
            require(row["graph"] == f"{KINDS[algorithm]}_{density}", f"Wrong graph kind for {algorithm}")
            n, m = row["n"], row["m"]
            require(n in SIZES and m == DENSITIES[density] * n, f"Wrong size/density on row {line_number}")
            key = algorithm, density, n
            require(key not in seen, f"Duplicate benchmark row: {key}")
            seen.add(key)
            require(math.isfinite(row["time_ms"]) and row["time_ms"] >= 0, f"Invalid time for {key}")
            require(all(row[name] >= 0 for name in ("dsu_ops", "edge_scans", "comparisons", "max_depth")),
                    f"Negative counter for {key}")
            mst, scc, dag_sum = REFERENCE[n, density]
            expected_result = {"Kruskal": mst, "Prim": mst, "Kosaraju": scc,
                               "Kahn": n, "DagShortestPath": dag_sum}[algorithm]
            require(row["result"] == expected_result, f"Reference result mismatch for {key}")
            if algorithm == "Kruskal":
                require(n - 1 <= row["edge_scans"] <= m, f"Invalid Kruskal scan count for {key}")
                require(row["dsu_ops"] == 2 * row["edge_scans"] + n - 1,
                        f"Kruskal find/union counters are inconsistent for {key}")
            else:
                expected_scans = (2 if algorithm in ("Prim", "Kosaraju") else 1) * m
                require(row["edge_scans"] == expected_scans, f"Exact edge-scan identity failed for {key}")
                require(row["dsu_ops"] == 0, f"Unexpected DSU operations for {key}")
            if algorithm not in ("Kruskal", "Prim"):
                require(row["comparisons"] == 0, f"Unexpected weight comparisons for {key}")
            if algorithm == "Kosaraju":
                require(1 <= row["max_depth"] <= n, f"Invalid explicit stack depth for {key}")
            else:
                require(row["max_depth"] == 0, f"Unexpected explicit DFS stack for {key}")
            row["density"] = density
            row["operations"] = row["edge_scans"] + row["comparisons"] + row["dsu_ops"]
            row["scan_share"] = row["edge_scans"] / m
            rows.append(row)
    expected_keys = {(algorithm, density, n) for algorithm in KINDS
                     for density in DENSITIES for n in SIZES}
    require(len(rows) == 50 and seen == expected_keys, "Expected all 50 algorithm/size/density rows")
    return rows


def fit_log_log(x: list[int], y: list[int]) -> dict | None:
    """OLS fit log10(y) = slope * log10(x) + intercept; zeros have no log fit."""
    if any(value <= 0 for value in y):
        return None
    log_x, log_y = np.log10(x), np.log10(y)
    slope, intercept = np.polyfit(log_x, log_y, 1)
    residual = float(np.sum((log_y - (slope * log_x + intercept)) ** 2))
    variance = float(np.sum((log_y - log_y.mean()) ** 2))
    r_squared = 1.0 - residual / variance if variance > 0 else 1.0
    return {"slope": float(slope), "intercept_log10": float(intercept),
            "r_squared": float(r_squared)}


def range_summary(values: list[float]) -> dict:
    return {"min": min(values), "max": max(values), "values": values}


def analyze(rows: list[dict], csv_path: Path) -> dict:
    analysis = {
        "schema_version": 1,
        "csv_sha256": hashlib.sha256(csv_path.read_bytes()).hexdigest(),
        "row_count": len(rows),
        "sizes": list(SIZES),
        "density_multipliers": DENSITIES,
        "operation_definition": "edge_scans + comparisons + dsu_ops; max_depth excluded",
        "fit_definition": "OLS on log10(counter) versus log10(n), all five sizes; zero series has null fit",
        "counter_scope": "Phase counters: sorted candidates, adjacency visits, DFS passes, outgoing-edge removals, or DAG relaxations. Initial in-degrees, transpose construction, and the DAG's separate Kahn call are excluded.",
        "series": {},
    }
    for algorithm in KINDS:
        analysis["series"][algorithm] = {}
        for density in DENSITIES:
            points = sorted((row for row in rows if row["algorithm"] == algorithm
                             and row["density"] == density), key=lambda row: row["n"])
            fits = {counter: fit_log_log([p["n"] for p in points], [p[counter] for p in points])
                    for counter in ("edge_scans", "comparisons", "operations")}
            ratios = {
                "scans_over_v_plus_e": range_summary([p["edge_scans"] / (p["n"] + p["m"]) for p in points]),
                "comparisons_over_e_log2_e": range_summary([p["comparisons"] / (p["m"] * math.log2(p["m"])) for p in points]),
                "dsu_ops_over_e": range_summary([p["dsu_ops"] / p["m"] for p in points]),
                "scans_over_e": range_summary([p["scan_share"] for p in points]),
            }
            times = [p["time_ms"] for p in points]
            analysis["series"][algorithm][density] = {
                "points": [{key: value for key, value in p.items()
                            if key not in ("algorithm", "graph", "density")} for p in points],
                "fits": fits,
                "ratios": ratios,
                "time_ms": {"min": min(times), "max": max(times), "first": times[0], "last": times[-1]},
            }
    return analysis


def configure_style() -> None:
    plt.rcParams.update({
        "font.family": "DejaVu Sans", "font.size": 11,
        "axes.titlesize": 13, "axes.titleweight": "bold", "axes.labelsize": 11.5,
        "axes.labelcolor": "#283649", "text.color": "#182638",
        "xtick.color": "#435266", "ytick.color": "#435266",
        "xtick.labelsize": 10, "ytick.labelsize": 10,
        "axes.spines.top": False, "axes.spines.right": False,
        "axes.edgecolor": "#B3BDCA", "axes.linewidth": 0.8,
        "figure.facecolor": "white", "axes.facecolor": "white",
        "legend.frameon": False, "legend.fontsize": 10.5,
        "savefig.facecolor": "white", "savefig.dpi": 220,
    })


def setup_axis(ax) -> None:
    ax.set_xscale("log")
    ax.set_xlim(760, 132_000)
    ax.xaxis.set_major_locator(FixedLocator(SIZES))
    tick_labels = dict(zip(SIZES, ("1,000", "3,162", "10,000", "31,623", "100,000")))
    ax.xaxis.set_major_formatter(FuncFormatter(lambda value, _: tick_labels.get(round(value), "")))
    ax.xaxis.set_minor_formatter(NullFormatter())
    ax.tick_params(axis="both", which="both", length=0, pad=7)
    ax.set_xlabel("Vertices, n", labelpad=9)
    ax.set_axisbelow(True)
    ax.grid(axis="both", which="major", color="#DFE5EB", linewidth=0.8)


def plot_series(ax, points: list[dict], algorithm: str, counter: str):
    style = STYLES[algorithm]
    return ax.plot([p["n"] for p in points], [p[counter] for p in points],
                   label=LABELS[algorithm], markersize=6.5,
                   markerfacecolor="white", markeredgewidth=1.5, **style)[0]


def title_and_legend(fig, axes, title: str, subtitle: str) -> None:
    fig.suptitle(title, x=0.075, y=0.985, ha="left", fontsize=19, fontweight="bold")
    fig.text(0.075, 0.914, subtitle, fontsize=11.3, color="#546276")
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="upper center", bbox_to_anchor=(0.53, 0.875),
               ncol=5, columnspacing=1.7, handlelength=2.4)


def operations_plot(analysis: dict, output: Path) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(12, 5), sharey=True)
    fig.subplots_adjust(left=0.075, right=0.98, bottom=0.205, top=0.735, wspace=0.13)
    for ax, density in zip(axes, DENSITIES):
        setup_axis(ax)
        ax.set_yscale("log")
        for algorithm in KINDS:
            plot_series(ax, analysis["series"][algorithm][density]["points"], algorithm, "operations")
        ax.set_title(f"{density.capitalize()} · m = {DENSITIES[density]}n", pad=11)
    axes[0].set_ylabel("Recorded operations", labelpad=8)
    title_and_legend(fig, axes, "Operation growth across graph sizes",
                     "Both axes use a logarithmic scale; each point comes from the validated benchmark CSV.")
    fig.text(0.075, 0.062, "Operations = edge scans + weight comparisons + DSU calls; stack depth is excluded.",
             fontsize=10.4, color="#546276")
    fig.text(0.075, 0.022, "Kahn and DAG shortest paths coincide exactly: both markers use the same data coordinates.",
             fontsize=10.4, color="#546276")
    fig.savefig(output / "operations-vs-n.png")
    plt.close(fig)


def scans_plot(analysis: dict, output: Path) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.8), sharey=True)
    fig.subplots_adjust(left=0.075, right=0.98, bottom=0.21, top=0.725, wspace=0.13)
    for ax, density in zip(axes, DENSITIES):
        setup_axis(ax)
        for algorithm in KINDS:
            plot_series(ax, analysis["series"][algorithm][density]["points"], algorithm, "scan_share")
        ax.set_title(f"{density.capitalize()} · m = {DENSITIES[density]}n", pad=11)
        ax.set_ylim(0, 2.43)
        ax.set_yticks([0, 0.5, 1, 1.5, 2])
        ax.annotate("Prim = Kosaraju: 2", (100_000, 2), xytext=(-3, 10),
                    textcoords="offset points", ha="right", fontsize=10.2, color="#253C32")
        ax.annotate("Kahn = DAG: 1", (100_000, 1), xytext=(-3, 10),
                    textcoords="offset points", ha="right", fontsize=10.2, color="#5D4366")
    axes[0].set_ylabel("Edge scans / input edges, m", labelpad=9)
    title_and_legend(fig, axes, "Scans per input edge",
                     "A flat curve shows constant work per edge in the counted phase; the vertex axis is logarithmic.")
    fig.text(0.075, 0.064, "Exact overlaps are shown without offsets. Undirected adjacency contains two entries per input edge.",
             fontsize=10.3, color="#546276")
    fig.text(0.075, 0.022, "Initial in-degree scans, transpose construction, and the DAG's separate Kahn call are outside these phase counters.",
             fontsize=10.3, color="#546276")
    fig.savefig(output / "scans-per-edge.png")
    plt.close(fig)


def kruskal_plot(analysis: dict, output: Path) -> None:
    fig, ax = plt.subplots(figsize=(12, 4.4))
    fig.subplots_adjust(left=0.075, right=0.98, bottom=0.22, top=0.74)
    setup_axis(ax)
    density_styles = {
        "sparse": dict(color="#0072B2", linestyle="-", marker="s"),
        "dense": dict(color="#D55E00", linestyle="--", marker="D"),
    }
    for density, style in density_styles.items():
        points = analysis["series"]["Kruskal"][density]["points"]
        ax.plot([p["n"] for p in points], [p["scan_share"] for p in points],
                linewidth=2.3, markersize=7, markerfacecolor="white", markeredgewidth=1.7,
                label=f"{density.capitalize()} · m = {DENSITIES[density]}n", **style)
        for point in points:
            ax.annotate(f"{point['scan_share']:.1%}", (point["n"], point["scan_share"]),
                        xytext=(0, 12 if density == "sparse" else -20),
                        textcoords="offset points", ha="center", fontsize=11,
                        fontweight="bold", color=style["color"])
    ax.set_ylim(0, 1.14)
    ax.set_yticks(np.arange(0, 1.01, 0.2))
    ax.yaxis.set_major_formatter(PercentFormatter(1, decimals=0))
    ax.set_ylabel("Share examined: edge scans / m", labelpad=9)
    ax.legend(loc="center left", bbox_to_anchor=(0.015, 0.50), fontsize=11)
    fig.suptitle("How much of the sorted edge list does Kruskal inspect?",
                 x=0.075, y=0.985, ha="left", fontsize=18, fontweight="bold")
    fig.text(0.075, 0.89, "Scanning stops after the spanning tree is complete; every input edge was sorted first.",
             fontsize=11.3, color="#546276")
    fig.text(0.075, 0.065, "A smaller examined share saves DSU calls, while sorting still compares weights across the full edge list.",
             fontsize=10.6, color="#546276")
    fig.text(0.075, 0.02, "Percentages are measured values for the seed-42 graphs, not a bound for every graph.",
             fontsize=10.6, color="#546276")
    fig.savefig(output / "kruskal-examined-share.png")
    plt.close(fig)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv", type=Path, default=ROOT / "results/results.csv")
    parser.add_argument("--output", type=Path, default=ROOT / "results/plots", help="PNG output directory")
    parser.add_argument("--analysis", type=Path, default=None, help="JSON path (default: output parent / analysis.json)")
    args = parser.parse_args()
    try:
        rows = read_results(args.csv)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    analysis = analyze(rows, args.csv)
    args.output.mkdir(parents=True, exist_ok=True)
    analysis_path = args.analysis or args.output.parent / "analysis.json"
    analysis_path.parent.mkdir(parents=True, exist_ok=True)
    configure_style()
    operations_plot(analysis, args.output)
    scans_plot(analysis, args.output)
    kruskal_plot(analysis, args.output)
    analysis_path.write_text(json.dumps(analysis, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(f"Validated {len(rows)} rows and all assignment reference results.")
    print(f"Saved three plots to {args.output.resolve()}")
    print(f"Saved fitted slopes and ratio ranges to {analysis_path.resolve()}")


if __name__ == "__main__":
    main()
