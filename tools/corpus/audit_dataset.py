#!/usr/bin/env python3
"""Audit dataset readiness without printing or exporting conversation text."""
from __future__ import annotations

import argparse
import json
import statistics
from collections import Counter, defaultdict
from pathlib import Path

from validate_corpus import validate_file

try:
    from tools.corpus.train_tokenizer import CharacterBPE
except ModuleNotFoundError:
    from train_tokenizer import CharacterBPE


def distribution(values: list[int]) -> dict:
    if not values:
        return {"count": 0, "min": None, "median": None, "p90": None, "max": None, "mean": None}
    ordered = sorted(values)
    p90_index = max(0, (9 * len(ordered) + 9) // 10 - 1)
    return {
        "count": len(ordered),
        "min": ordered[0],
        "median": statistics.median(ordered),
        "p90": ordered[p90_index],
        "max": ordered[-1],
        "mean": round(statistics.fmean(ordered), 2),
    }


def record_lengths(record: dict, tokenizer: CharacterBPE | None) -> tuple[int, int | None]:
    messages = record["messages"]
    character_count = sum(len(message["content"]) for message in messages)
    if tokenizer is None:
        return character_count, None
    token_count = sum(
        len(tokenizer.encode(message["role"] + ": " + message["content"] + "\\n"))
        for message in messages
    ) + 2  # BOS and EOS
    return character_count, token_count


def audit_dataset(path: Path, context_length: int = 256, tokenizer_path: Path | None = None) -> dict:
    errors, validation_stats = validate_file(path)
    if errors:
        return {
            "status": "invalid",
            "validation": validation_stats,
            "error_count": len(errors),
            "errors": errors[:20],
            "errors_truncated": len(errors) > 20,
        }

    tokenizer = CharacterBPE.load(tokenizer_path) if tokenizer_path else None
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
    chars_by_split = defaultdict(list)
    tokens_by_split = defaultdict(list)
    eligible_token_lengths = []

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
                char_count, token_count = record_lengths(record, tokenizer)
                chars_by_split[split].append(char_count)
                if token_count is not None:
                    tokens_by_split[split].append(token_count)
                    eligible_token_lengths.append(token_count)

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

    length_report = {
        "characters_per_example": {
            split: distribution(chars_by_split[split]) for split in ("train", "validation", "test")
        },
        "tokenizer_supplied": tokenizer is not None,
        "tokenizer_vocab_size": len(tokenizer.vocab) if tokenizer else None,
        "tokens_per_example": {
            split: distribution(tokens_by_split[split]) for split in ("train", "validation", "test")
        } if tokenizer else None,
        "token_context_fit": {
            "context_length": context_length,
            "examples_over_context": sum(value > context_length for value in eligible_token_lengths),
            "examples_at_or_under_context": sum(value <= context_length for value in eligible_token_lengths),
            "note": "Perbandingan panjang contoh utuh; trainer mengambil jendela konteks dan bukan membuang contoh panjang."
                    if tokenizer else "Berikan --tokenizer untuk mengukur panjang token terhadap context length.",
        },
    }
    if tokenizer and eligible_token_lengths and max(eligible_token_lengths) > context_length:
        warnings.append("Sebagian contoh lebih panjang dari context length; trainer memotongnya menjadi jendela, periksa apakah batas giliran tetap terwakili")
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
        "length_analysis": length_report,
        "blockers": blockers,
        "warnings": warnings,
        "privacy_note": "Tidak ada isi percakapan, nama pengguna, atau teks mentah yang ditampilkan dalam laporan.",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jsonl", type=Path)
    parser.add_argument("--context-length", type=int, default=256)
    parser.add_argument("--tokenizer", type=Path, help="tokenizer JSON untuk mengukur panjang token")
    args = parser.parse_args()
    if args.context_length < 2:
        parser.error("context-length minimal 2")
    report = audit_dataset(args.jsonl, args.context_length, args.tokenizer)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 1 if report["status"] == "invalid" else 0 if report["status"] == "ready_for_pilot_review" else 2


if __name__ == "__main__":
    raise SystemExit(main())
