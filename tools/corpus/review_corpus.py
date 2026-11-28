#!/usr/bin/env python3
"""Apply explicit human review decisions to a NEW corpus copy; never edit source in place."""
from __future__ import annotations
import argparse, json, os, tempfile
from pathlib import Path
from typing import Any
try:
    from tools.corpus.validate_corpus import validate_file, validate_record
except ModuleNotFoundError:
    from validate_corpus import validate_file, validate_record


def apply_reviews(corpus_path: Path, decisions_path: Path, output_path: Path) -> dict:
    if output_path.exists():
        raise FileExistsError("Output sudah ada; gunakan nama file baru agar tidak menimpa data.")
    errors, stats = validate_file(corpus_path)
    if errors:
        raise ValueError("Korpus sumber tidak valid: " + "; ".join(errors[:5]))
    records = [json.loads(line) for line in corpus_path.read_text(encoding="utf-8").splitlines()]
    payload = json.loads(decisions_path.read_text(encoding="utf-8"))
    decisions = payload.get("decisions") if isinstance(payload, dict) else None
    if not isinstance(decisions, list) or not decisions:
        raise ValueError("File keputusan harus berisi array decisions yang tidak kosong.")
    by_id = {record["example_id"]: record for record in records}
    seen: set[str] = set()
    counts = {"approved": 0, "rejected": 0, "corrected_pending": 0}
    for index, decision in enumerate(decisions):
        if not isinstance(decision, dict):
            raise ValueError(f"Keputusan nomor {index + 1} harus berupa objek.")
        example_id, action, reviewer = decision.get("example_id"), decision.get("action"), decision.get("reviewer")
        if not isinstance(example_id, str) or example_id not in by_id:
            raise ValueError(f"example_id tidak ditemukan pada keputusan nomor {index + 1}.")
        if example_id in seen:
            raise ValueError(f"Keputusan ganda untuk example_id {example_id}.")
        seen.add(example_id)
        if not isinstance(reviewer, str) or not reviewer.strip() or len(reviewer) > 120:
            raise ValueError(f"Reviewer wajib diisi (maksimal 120 karakter) untuk {example_id}.")
        record = by_id[example_id]
        if action == "approve":
            if record.get("privacy_review") != "passed":
                raise ValueError(f"{example_id}: persetujuan kualitas memerlukan privacy_review passed.")
            record["quality_review"] = "passed"
            counts["approved"] += 1
        elif action == "reject":
            record["quality_review"] = "rejected"
            counts["rejected"] += 1
        elif action == "correct":
            corrected = decision.get("messages")
            if not isinstance(corrected, list):
                raise ValueError(f"{example_id}: koreksi wajib menyertakan messages.")
            record["messages"] = corrected
            record["privacy_review"] = "pending"
            record["quality_review"] = "pending"
            record["split"] = "unassigned"
            counts["corrected_pending"] += 1
        else:
            raise ValueError(f"Aksi tidak dikenal untuk {example_id}; gunakan approve, reject, atau correct.")
        history = record.setdefault("review_history", [])
        if not isinstance(history, list):
            raise ValueError(f"{example_id}: review_history tidak valid.")
        history.append({"reviewer": reviewer.strip(), "action": action})
    for record in records:
        record_errors = validate_record(record, 0)
        if record_errors:
            raise ValueError(f"Hasil keputusan membuat {record.get('example_id')} tidak valid: " + "; ".join(record_errors[:4]))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=output_path.parent,
                prefix=output_path.name + ".", suffix=".tmp", delete=False) as handle:
            temporary = Path(handle.name)
            for record in records:
                handle.write(json.dumps(record, ensure_ascii=False) + "\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, output_path)
    finally:
        if temporary and temporary.exists():
            temporary.unlink()
    final_errors, final_stats = validate_file(output_path)
    if final_errors:
        output_path.unlink(missing_ok=True)
        raise ValueError("Korpus hasil tidak lolos validasi: " + "; ".join(final_errors[:5]))
    return {"status": "created_reviewed_copy", "source_records": stats["records"],
        "decisions_applied": len(decisions), "decision_counts": counts,
        "output_records": final_stats["records"], "source_modified": False,
        "note": "Koreksi mengembalikan privacy_review dan quality_review ke pending serta split ke unassigned; wajib ditinjau ulang dan dibagi ulang sebelum training."}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("corpus", type=Path)
    parser.add_argument("--decisions", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = apply_reviews(args.corpus, args.decisions, args.output)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        parser.error(str(exc))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
