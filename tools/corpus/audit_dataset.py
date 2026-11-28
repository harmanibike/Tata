#!/usr/bin/env python3
"""Audit dataset readiness without printing or exporting conversation text."""
from __future__ import annotations

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

from validate_corpus import validate_file


def audit_dataset(path: Path, context_length: int = 256) -> dict:
    errors, validation_stats = validate_file(path)
    if errors:
        return {
            "status": "invalid",
            "validation": validation_stats,
            "error_count": len(errors),
            "errors": errors[:20],
            "errors_truncated": len(errors) > 20,
        }

    records = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    quality = Counter()
    privacy = Counter()
    splits = Counter()
    split_sources = defaultdict(set)
    licenses = Counter()
    languages = Counter()
    tasks = Counter()
    approved_sources = set()
    approved_examples = 0
    for record in records:
        quality[record.get("quality_review", "missing")] += 1
        privacy[record.get("privacy_review", "missing")] += 1
        split = record.get("split", "unassigned")
        splits[split] += 1
        languages[record.get("language", "missing")] += 1
        tasks[record.get("task", "missing")] += 1
        source = record.get("source") or {}
        licenses[source.get("license", "missing")] += 1
        source_id = source.get("source_id")
        if record.get("privacy_review") == "passed" and record.get("quality_review") == "passed":
            approved_examples += 1
            approved_sources.add(source_id)
            if split in {"train", "validation", "test"}:
                split_sources[split].add(source_id)

    blockers = []
    warnings = []
    if approved_examples == 0:
        blockers.append("Belum ada contoh yang lolos tinjauan privasi dan kualitas")
    if len(approved_sources) < 3:
        blockers.append("Minimal tiga source_id berbeda yang disetujui diperlukan untuk train/validation/test")
    for split in ("train", "validation", "test"):
        if splits[split] == 0:
            blockers.append(f"Split {split} masih kosong")
        if split_sources[split] == 0:
            blockers.append(f"Belum ada source_id disetujui pada split {split}")
    if len(approved_sources) < 20:
        warnings.append("Jumlah sumber disetujui masih sangat kecil untuk eksperimen yang representatif")
    if approved_examples < 1000:
        warnings.append("Jumlah contoh disetujui di bawah 1.000; hasil hanya layak dianggap pilot pipeline, bukan asisten umum")
    if any(quality[key] for key in ("pending", "missing")):
        warnings.append("Masih ada contoh yang menunggu tinjauan kualitas atau belum memiliki status")
    return {
        "status": "blocked" if blockers else "ready_for_pilot_review",
        "records_total": len(records),
        "approved_examples": approved_examples,
        "approved_source_groups": len(approved_sources),
        "quality_review": dict(sorted(quality.items())),
        "privacy_review": dict(sorted(privacy.items())),
        "split_examples": {key: splits[key] for key in ("train", "validation", "test", "unassigned")},
        "split_source_groups": {key: len(split_sources[key]) for key in ("train", "validation", "test")},
        "licenses": dict(sorted(licenses.items())),
        "languages": dict(sorted(languages.items())),
        "tasks": dict(sorted(tasks.items())),
        "context_length_requested": context_length,
        "blockers": blockers,
        "warnings": warnings,
        "privacy_note": "Tidak ada isi percakapan, nama pengguna, atau teks mentah yang ditampilkan dalam laporan.",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jsonl", type=Path)
    parser.add_argument("--context-length", type=int, default=256)
    args = parser.parse_args()
    if args.context_length < 2:
        parser.error("context-length minimal 2")
    report = audit_dataset(args.jsonl, args.context_length)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 1 if report["status"] == "invalid" else 0 if report["status"] == "ready_for_pilot_review" else 2


if __name__ == "__main__":
    raise SystemExit(main())
