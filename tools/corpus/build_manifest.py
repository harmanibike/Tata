#!/usr/bin/env python3
"""Create a privacy-minimal provenance manifest after successful corpus validation."""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import Counter
from pathlib import Path

from validate_corpus import validate_file


def create_manifest(corpus_path: Path) -> dict:
    errors, stats = validate_file(corpus_path)
    if errors:
        raise ValueError("Korpus tidak lolos validasi: " + "; ".join(errors[:5]))

    raw = corpus_path.read_bytes()
    records = [
        json.loads(line)
        for line in raw.decode("utf-8").splitlines()
        if line.strip()
    ]
    if any(record.get("privacy_review") != "passed" or record.get("quality_review") != "passed" for record in records):
        raise ValueError("Manifest hanya boleh dibuat jika seluruh contoh lolos tinjauan privasi dan kualitas.")

    source_map: dict[str, dict] = {}
    split_counts: Counter[str] = Counter()

    for record in records:
        source = record["source"]
        source_id = source["source_id"]
        if source_id not in source_map:
            source_map[source_id] = {
                "title": source["title"],
                "license": source["license"],
                "example_count": 0,
            }
        elif (
            source_map[source_id]["title"] != source["title"]
            or source_map[source_id]["license"] != source["license"]
        ):
            raise ValueError(f"Metadata sumber tidak konsisten: {source_id}")
        source_map[source_id]["example_count"] += 1
        split_counts[record.get("split", "unassigned")] += 1

    return {
        "manifest_schema_version": 1,
        "corpus_sha256": hashlib.sha256(raw).hexdigest(),
        "record_count": stats["records"],
        "source_count": len(source_map),
        "sources": [
            {"source_id": key, **source_map[key]}
            for key in sorted(source_map)
        ],
        "split_counts": dict(sorted(split_counts.items())),
        "validation": {
            "status": "passed",
            "validator": "tata-corpus-validator",
            "validator_version": "0.1.0",
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jsonl", type=Path, help="file korpus JSONL")
    parser.add_argument("--output", type=Path, required=True, help="lokasi manifest JSON")
    args = parser.parse_args()
    try:
        manifest = create_manifest(args.jsonl)
        args.output.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    except (OSError, UnicodeError, ValueError) as exc:
        print(f"Gagal membuat manifest: {exc}", file=sys.stderr)
        return 1
    print(f"Manifest berhasil dibuat: {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
