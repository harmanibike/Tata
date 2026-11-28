#!/usr/bin/env python3
"""Validate Tata AI source-register CSV and fail closed on unproven training rights."""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from datetime import date
from pathlib import Path

REQUIRED_COLUMNS = {
    "source_id", "title", "owner", "canonical_uri", "accessed_at",
    "declared_license", "license_evidence_uri", "training_permission",
    "commercial_use", "derivative_permission", "attribution_required",
    "redistribution_allowed", "privacy_screen", "reviewer", "decision",
    "decision_notes",
}
YES_NO_UNCLEAR = {"yes", "no", "unclear"}
DECISIONS = {"pending", "approved", "rejected"}
URI_RE = re.compile(r"^https?://\S+$", re.I)


def validate_register(path: Path) -> tuple[list[str], dict]:
    errors: list[str] = []
    stats = {"sources": 0, "approved": 0, "pending": 0, "rejected": 0}
    try:
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            columns = set(reader.fieldnames or [])
            missing = sorted(REQUIRED_COLUMNS - columns)
            if missing:
                return [f"Kolom wajib tidak ada: {', '.join(missing)}"], stats
            seen: set[str] = set()
            for line_number, row in enumerate(reader, start=2):
                stats["sources"] += 1
                source_id = (row.get("source_id") or "").strip()
                if not source_id:
                    errors.append(f"baris {line_number}: source_id wajib diisi")
                elif source_id in seen:
                    errors.append(f"baris {line_number}: source_id duplikat: {source_id}")
                seen.add(source_id)

                for field in ("title", "owner", "declared_license", "reviewer", "privacy_screen"):
                    if not (row.get(field) or "").strip():
                        errors.append(f"baris {line_number}: {field} wajib diisi")

                for field in ("canonical_uri", "license_evidence_uri"):
                    value = (row.get(field) or "").strip()
                    if not URI_RE.fullmatch(value):
                        errors.append(f"baris {line_number}: {field} harus berupa URL http/https yang dapat diperiksa")

                accessed_at = (row.get("accessed_at") or "").strip()
                try:
                    if date.fromisoformat(accessed_at).isoformat() != accessed_at:
                        raise ValueError
                except ValueError:
                    errors.append(f"baris {line_number}: accessed_at harus YYYY-MM-DD yang valid")

                for field in ("training_permission", "commercial_use", "derivative_permission", "redistribution_allowed"):
                    value = (row.get(field) or "").strip().lower()
                    if value not in YES_NO_UNCLEAR:
                        errors.append(f"baris {line_number}: {field} harus yes, no, atau unclear")

                decision = (row.get("decision") or "").strip().lower()
                if decision not in DECISIONS:
                    errors.append(f"baris {line_number}: decision harus pending, approved, atau rejected")
                    continue
                stats[decision] += 1

                if decision == "approved":
                    blockers = []
                    if (row.get("training_permission") or "").strip().lower() != "yes":
                        blockers.append("training_permission bukan yes")
                    if not (row.get("license_evidence_uri") or "").strip():
                        blockers.append("bukti lisensi/izin tidak ada")
                    if not (row.get("decision_notes") or "").strip():
                        blockers.append("decision_notes wajib menjelaskan dasar persetujuan")
                    if blockers:
                        errors.append(
                            f"baris {line_number}: sumber tidak boleh approved: " + "; ".join(blockers)
                        )
    except (OSError, UnicodeError, csv.Error) as exc:
        return [f"Gagal membaca register CSV: {exc}"], stats

    if stats["sources"] == 0:
        errors.append("Register belum berisi sumber; belum ada sumber yang dapat disetujui.")
    return errors, stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_file", type=Path, help="register sumber dataset CSV UTF-8")
    args = parser.parse_args()
    errors, stats = validate_register(args.csv_file)
    report = {
        "status": "failed" if errors else "passed",
        **stats,
        "approved_training_sources": stats["approved"] if not errors else 0,
        "errors": errors,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
