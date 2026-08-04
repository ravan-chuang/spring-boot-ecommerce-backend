#!/usr/bin/env python3
"""Check JaCoCo bundle coverage against a committed regression baseline."""

from __future__ import annotations

import argparse
import csv
import json
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Coverage:
    instruction: float
    branch: float


def ratio(covered: int, missed: int) -> float:
    total = covered + missed
    return covered / total if total else 1.0


def read_jacoco_csv(path: Path) -> Coverage:
    if not path.is_file():
        raise FileNotFoundError(f"JaCoCo CSV not found: {path}")

    instruction_covered = instruction_missed = 0
    branch_covered = branch_missed = 0

    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        required = {
            "INSTRUCTION_MISSED",
            "INSTRUCTION_COVERED",
            "BRANCH_MISSED",
            "BRANCH_COVERED",
        }
        if not reader.fieldnames or not required.issubset(reader.fieldnames):
            raise ValueError(
                f"{path} is missing required JaCoCo columns: "
                f"{sorted(required - set(reader.fieldnames or []))}"
            )

        for row in reader:
            instruction_missed += int(row["INSTRUCTION_MISSED"])
            instruction_covered += int(row["INSTRUCTION_COVERED"])
            branch_missed += int(row["BRANCH_MISSED"])
            branch_covered += int(row["BRANCH_COVERED"])

    return Coverage(
        instruction=ratio(instruction_covered, instruction_missed),
        branch=ratio(branch_covered, branch_missed),
    )


def load_baseline(path: Path) -> dict:
    if not path.is_file():
        raise FileNotFoundError(f"Coverage baseline not found: {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    for key in ("instruction_ratio", "branch_ratio", "maximum_drop"):
        if key not in data:
            raise ValueError(f"Coverage baseline is missing '{key}'")
    return data


def pct(value: float) -> str:
    return f"{value * 100:.2f}%"


def check(current: Coverage, baseline: dict) -> int:
    allowed_drop = float(baseline["maximum_drop"])
    expected = Coverage(
        instruction=float(baseline["instruction_ratio"]),
        branch=float(baseline["branch_ratio"]),
    )

    print("Coverage baseline comparison")
    print(f"  Instruction: current {pct(current.instruction)}, "
          f"baseline {pct(expected.instruction)}, "
          f"allowed drop {pct(allowed_drop)}")
    print(f"  Branch:      current {pct(current.branch)}, "
          f"baseline {pct(expected.branch)}, "
          f"allowed drop {pct(allowed_drop)}")

    failures: list[str] = []
    if current.instruction + allowed_drop < expected.instruction:
        failures.append(
            f"instruction coverage regressed from {pct(expected.instruction)} "
            f"to {pct(current.instruction)}"
        )
    if current.branch + allowed_drop < expected.branch:
        failures.append(
            f"branch coverage regressed from {pct(expected.branch)} "
            f"to {pct(current.branch)}"
        )

    if failures:
        print("\nCoverage regression detected:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1

    print("\nCoverage baseline check passed.")
    return 0


def snapshot(current: Coverage, output: Path, maximum_drop: float) -> None:
    payload = {
        "schema_version": 1,
        "instruction_ratio": round(current.instruction, 6),
        "branch_ratio": round(current.branch, 6),
        "maximum_drop": maximum_drop,
        "source": "Generated from target/site/jacoco/jacoco.csv",
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote coverage baseline to {output}")
    print(f"  Instruction: {pct(current.instruction)}")
    print(f"  Branch:      {pct(current.branch)}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("target/site/jacoco/jacoco.csv"),
        help="JaCoCo CSV report",
    )
    parser.add_argument(
        "--baseline",
        type=Path,
        default=Path("config/coverage-baseline.json"),
        help="Committed baseline JSON",
    )
    parser.add_argument(
        "--snapshot",
        action="store_true",
        help="Write a new baseline from the current JaCoCo report",
    )
    parser.add_argument(
        "--maximum-drop",
        type=float,
        default=0.005,
        help="Allowed absolute ratio drop when creating a snapshot (default: 0.005)",
    )
    args = parser.parse_args()

    try:
        current = read_jacoco_csv(args.report)
        if args.snapshot:
            snapshot(current, args.baseline, args.maximum_drop)
            return 0
        return check(current, load_baseline(args.baseline))
    except (FileNotFoundError, ValueError, json.JSONDecodeError) as exc:
        print(f"coverage-baseline error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
