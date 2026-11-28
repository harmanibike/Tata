#!/usr/bin/env python3
"""Export a privacy-minimal queue of corpus examples that need human review."""
from __future__ import annotations
import argparse, json, os, tempfile
from pathlib import Path
try:
    from tools.corpus.validate_corpus import validate_file
    from tools.corpus.audit_dataset import near_duplicate_prompt_stats
except ModuleNotFoundError:
    from validate_corpus import validate_file
    from audit_dataset import near_duplicate_prompt_stats


def build_review_queue(corpus_path: Path, output_path: Path) -> dict:
    if output_path.exists():
        raise FileExistsError("Output antrean sudah ada; pilih nama file baru.")
    errors, stats = validate_file(corpus_path)
    if errors:
        raise ValueError("Korpus tidak valid: " + "; ".join(errors[:5]))
    records = [json.loads(line) for line in corpus_path.read_text(encoding="utf-8").splitlines()]
    queue: dict[str, dict] = {}
    for record in records:
        privacy = record.get("privacy_review")
        quality = record.get("quality_review")
        reasons = []
        if privacy == "pending":
            reasons.append("privacy_review_pending")
        if quality == "pending":
            reasons.append("quality_review_pending")
        if reasons:
            queue[record["example_id"]] = {
                "example_id": record["example_id"],
                "source_id": record["source"]["source_id"],
                "license": record["source"]["license"],
                "split": record.get("split", "unassigned"),
                "privacy_review": privacy,
                "quality_review": quality,
                "reasons": reasons,
                "related_examples": [],
            }
    duplicate_stats = near_duplicate_prompt_stats(records)
    for pair in duplicate_stats["review_candidates"]:
        for current, related in (
            (pair["example_id_a"], pair["example_id_b"]),
            (pair["example_id_b"], pair["example_id_a"]),
        ):
            item = queue.setdefault(current, {
                "example_id": current,
                "source_id": None,
                "license": None,
                "split": None,
                "privacy_review": "passed",
                "quality_review": "passed",
                "reasons": [],
                "related_examples": [],
            })
            item["reasons"].append(pair["reason"])
            item["related_examples"].append({
                "example_id": related,
                "similarity": pair["similarity"],
                "answers_differ": pair["answers_differ"],
            })
    entries = sorted(queue.values(), key=lambda item: item["example_id"])
    payload = {
        "queue_schema_version": 1,
        "source_record_count": stats["records"],
        "review_item_count": len(entries),
        "items": entries,
        "raw_conversation_text_included": False,
        "note": "Gunakan example_id untuk membuka contoh pada korpus lokal. Kandidat mirip adalah petunjuk leksikal dan perlu diperiksa manusia.",
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=output_path.parent,
                prefix=output_path.name + ".", suffix=".tmp", delete=False) as handle:
            temporary = Path(handle.name)
            handle.write(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, output_path)
    finally:
        if temporary and temporary.exists():
            temporary.unlink()
    return {"status": "created", "source_records": stats["records"],
            "review_items": len(entries), "output": str(output_path),
            "raw_conversation_text_included": False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("corpus", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = build_review_queue(args.corpus, args.output)
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
