#!/usr/bin/env bash
set -euo pipefail

REPRO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPRO_PYTHON="${PYTHON:-python3}"
cd "$REPRO_ROOT"
export MPLCONFIGDIR="${MPLCONFIGDIR:-$REPRO_ROOT/.mplconfig}"

command -v mvn >/dev/null
command -v java >/dev/null
command -v "$REPRO_PYTHON" >/dev/null

# Install scripts/requirements.txt in the selected Python environment first.
"$REPRO_PYTHON" -c 'import matplotlib, numpy, reportlab, pypdf'

mvn -B clean verify
mkdir -p results
java -Xms256m -Xmx3g -jar target/graph-benchmark.jar | tee results/benchmark.log
java -Xms256m -Xmx3g -cp target/graph-benchmark.jar benchmark.BonusBenchmark | tee results/bonus-benchmark.log

"$REPRO_PYTHON" - <<'PY'
import csv
import hashlib
import json
import os
import platform
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path


def version(*args):
    result = subprocess.run(args, check=True, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return re.sub(r'\x1b\[[0-9;]*m', '', result.stdout).strip()


processor = platform.processor() or 'unavailable'
cpuinfo = Path('/proc/cpuinfo')
if cpuinfo.is_file():
    for line in cpuinfo.read_text().splitlines():
        if line.startswith('model name'):
            processor = line.split(':', 1)[1].strip()
            break

test_files = sorted(Path('target/surefire-reports').glob('TEST-*.xml'))
if not test_files:
    raise RuntimeError('No JUnit XML reports found after Maven verify')
totals = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
for report in test_files:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, '0'))

with Path('results/results.csv').open(newline='') as stream:
    rows = list(csv.DictReader(stream))
if len(rows) != 50:
    raise RuntimeError(f'Expected 50 benchmark rows, found {len(rows)}')
with Path('results/bonus.csv').open(newline='') as stream:
    bonus_rows = list(csv.DictReader(stream))
if len(bonus_rows) != 40:
    raise RuntimeError(f'Expected 40 bonus rows, found {len(bonus_rows)}')

metadata = {
    'generated_at_utc': datetime.now(timezone.utc).isoformat(),
    'command': 'java -Xms256m -Xmx3g -jar target/graph-benchmark.jar',
    'java_options': ['-Xms256m', '-Xmx3g'],
    'java': version('java', '-version'),
    'maven': version('mvn', '-version'),
    'python': sys.version,
    'platform': platform.platform(),
    'machine': platform.machine(),
    'processor': processor,
    'logical_cpus': os.cpu_count(),
    'seed': 42,
    'runs_per_algorithm': 5,
    'warmup_runs': 0,
    'graph_sizes': [1000, 3162, 10000, 31623, 100000],
    'densities': {'sparse': 4, 'dense': 20},
    'configurations': 10,
    'csv_rows': len(rows),
    'csv_sha256': hashlib.sha256(Path('results/results.csv').read_bytes()).hexdigest(),
    'bonus_command': 'java -Xms256m -Xmx3g -cp target/graph-benchmark.jar benchmark.BonusBenchmark',
    'bonus_csv_rows': len(bonus_rows),
    'bonus_csv_sha256': hashlib.sha256(Path('results/bonus.csv').read_bytes()).hexdigest(),
    'os_pretty_name': platform.freedesktop_os_release().get('PRETTY_NAME', platform.platform()) if Path('/etc/os-release').exists() else platform.platform(),
    'tests': totals,
}
Path('results/environment.json').write_text(
    json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
print('Recorded environment and test totals in results/environment.json')
PY

"$REPRO_PYTHON" scripts/plot_results.py
"$REPRO_PYTHON" scripts/plot_bonus.py
"$REPRO_PYTHON" scripts/build_report.py
echo 'Reproduction complete: mandatory and bonus CSVs, five figures, and output/pdf/REPORT.pdf'
