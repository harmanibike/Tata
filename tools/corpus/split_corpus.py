#!/usr/bin/env python3
"""Assign deterministic train/validation/test splits without source leakage.

Only privacy- and quality-approved examples are assigned. Examples sharing a
source_id always remain in the same split. This tool never fetches data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
from collections import defaultdict
from pathlib import Path

from validate_corpus import validate_file


def allocate_group_counts(group_count: int, ratios: tuple[float, float, float]) -> tuple[int, int, int]:
    if group_count < 3:
        raise ValueError("Minimal tiga source_id berbeda diperlukan agar train, validation, dan test terisi")
    if any(value <= 0 for value in ratios) or abs(sum(ratios) - 1.0) > 1e-9:
        raise ValueError("Rasio train/validation/test harus positif dan totalnya 1")
    raw = [group_count * value for value in ratios]
    counts = [int(value) for value in raw]
    for index in range(3):
        if counts[index] == 0:
            counts[index] = 1
    while sum(counts) > group_count:
        candidates = [i for i, count in enumerate(counts) if count > 1]
        if not candidates:
            raise ValueError("Tidak cukup grup sumber untuk tiga split")
        index = min(candidates, key=lambda i: (raw[i] - int(raw[i]), -counts[i], i))
        counts[index] -= 1
    while sum(counts) < group_count:
        index = max(range(3), key=lambda i: (raw[i] - int(raw[i]), -i))
        counts[index] += 1
        raw[index] = int(raw[index])
    return tuple(counts)


def stable_split(source_id: str, seed: str) -> str:
    digest = hashlib.sha256(f"{seed}:{source_id}".encode("utf-8")).hexdigest()
    return digest


def assign_splits(records: list[dict], seed: str = "tata-v1",
                  ratios: tuple[float, float, float] = (0.9, 0.05, 0.05)) -> tuple[list[dict], dict[str, int]]:
    groups: dict[str, list[int]] = defaultdict(list)
    for index, record in enumerate(records):
        if record.get("privacy_review") == "passed" and record.get("quality_review") == "passed":
            source = record.get("source")
            source_id = source.get("source_id") if isinstance(source, dict) else None
            if not isinstance(source_id, str) or not source_id.strip():
                raise ValueError(f"Contoh {record.get('example_id', index)} tidak memiliki source_id")
            groups[source_id].append(index)

    counts = allocate_group_counts(len(groups), ratios)
    ordered = sorted(groups, key=lambda source_id: (stable_split(source_id, seed), source_id))
    labels = ("train", "validation", "test")
    assignments: dict[str, str] = {}
    cursor = 0
    for label, count in zip(labels, counts):
        for source_id in ordered[cursor:cursor + count]:
            assignments[source_id] = label
        cursor += count

    output = [dict(record) for record in records]
    example_counts = {label: 0 for label in labels}
    for source_id, indexes in groups.items():
        label = assignments[source_id]
        for index in indexes:
            output[index] = dict(output[index])
            output[index]["split"] = label
            example_counts[label] += 1
    return output, example_counts


def write_jsonl_atomic(path: Path, records: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    content = "".join(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
                      for record in records)
    temporary_name = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent,
                                         delete=False) as handle:
            temporary_name = handle.name
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_name, path)
    finally:
        if temporary_name and os.path.exists(temporary_name):
            os.unlink(temporary_name)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jsonl", type=Path, help="korpus JSONL yang telah divalidasi")
    parser.add_argument("--output", type=Path, required=True, help="file JSONL hasil pembagian")
    parser.add_argument("--seed", default="tata-v1", help="seed stabil untuk reproduksibilitas")
    parser.add_argument("--train", type=float, default=0.90)
    parser.add_argument("--validation", type=float, default=0.05)
    parser.add_argument("--test", type=float, default=0.05)
    args = parser.parse_args()

    errors, stats = validate_file(args.jsonl)
    if errors:
        print(json.dumps({"status": "failed", "errors": errors}, ensure_ascii=False, indent=2))
        return 1
    try:
        records = [json.loads(line) for line in args.jsonl.read_text(encoding="utf-8").splitlines()]
        assigned, counts = assign_splits(records, args.seed, (args.train, args.validation, args.test))
        write_jsonl_atomic(args.output, assigned)
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print(json.dumps({"status": "failed", "error": str(exc)}, ensure_ascii=False, indent=2))
        return 1

    pending = sum(1 for item in records
                  if item.get("privacy_review") != "passed" or item.get("quality_review") != "passed")
    print(json.dumps({
        "status": "assigned",
        "input_records": stats["records"],
        "approved_source_groups": len({item["source"]["source_id"] for item in records
                                       if item.get("privacy_review") == "passed"
                                       and item.get("quality_review") == "passed"}),
        "assigned_examples": sum(counts.values()),
        "pending_examples_left_unassigned": pending,
        "split_examples": counts,
        "seed": args.seed,
        "grouping": "source_id",
        "output": str(args.output),
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
