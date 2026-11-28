#!/usr/bin/env python3
"""Audit dataset readiness without printing or exporting conversation text."""
from __future__ import annotations

import argparse
import hashlib
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


def normalized_message_hash(record: dict, role: str) -> str:
    value = "\\n".join(
        message["content"].casefold().strip()
        for message in record["messages"] if message["role"] == role
    )
    normalized = " ".join(value.split())
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def record_lengths(record: dict, tokenizer: CharacterBPE | None) -> tuple[int, int | None]:
    messages = record["messages"]
    character_count = sum(len(message["content"]) for message in messages)
    if tokenizer is None:
        return character_count, None
    token_count = sum(
        len(tokenizer.encode(message["role"] + ": " + message["content"] + "\n"))
        for message in messages
    ) + 2
    return character_count, token_count




def character_ngrams(value: str, n: int = 3) -> Counter:
    normalized = " ".join(value.casefold().split())
    if not normalized:
        return Counter()
    padded = "  " + normalized + "  "
    if len(padded) < n:
        return Counter({padded: 1})
    return Counter(padded[index:index + n] for index in range(len(padded) - n + 1))


def cosine_similarity(left: Counter, right: Counter) -> float:
    if not left or not right:
        return 0.0
    dot = sum(count * right.get(token, 0) for token, count in left.items())
    left_norm = sum(value * value for value in left.values()) ** 0.5
    right_norm = sum(value * value for value in right.values()) ** 0.5
    return dot / (left_norm * right_norm) if left_norm and right_norm else 0.0


def near_duplicate_prompt_stats(records: list[dict], threshold: float = 0.82, max_candidates: int = 20000) -> dict:
    """Find lexical near-duplicate prompt candidates; never return raw prompt text."""
    items = []
    for record in records:
        if record.get("privacy_review") != "passed" or record.get("quality_review") != "passed":
            continue
        if record.get("split") not in {"train", "validation", "test"}:
            continue
        prompt = "\\n".join(
            message["content"] for message in record["messages"] if message["role"] == "user"
        )
        grams = character_ngrams(prompt)
        if grams:
            items.append((grams, normalized_message_hash(record, "user"),
                          normalized_message_hash(record, "assistant"),
                          record.get("example_id", "")))
    inverted = defaultdict(set)
    for index, item in enumerate(items):
        grams = item[0]
        for gram in grams:
            inverted[gram].add(index)
    candidate_pairs = set()
    truncated = False
    for index, (grams, _, _) in enumerate(items):
        possible = set()
        for gram in grams:
            possible.update(inverted[gram])
        for other in possible:
            if other <= index:
                continue
            candidate_pairs.add((index, other))
            if len(candidate_pairs) >= max_candidates:
                truncated = True
                break
        if truncated:
            break
    similar_pairs = 0
    conflicting_pairs = 0
    max_similarity = 0.0
    review_candidates = []
    for left, right in candidate_pairs:
        score = cosine_similarity(items[left][0], items[right][0])
        max_similarity = max(max_similarity, score)
        if score >= threshold:
            similar_pairs += 1
            answers_differ = items[left][2] != items[right][2]
            if answers_differ:
                conflicting_pairs += 1
            review_candidates.append({
                "example_id_a": items[left][3],
                "example_id_b": items[right][3],
                "similarity": round(score, 4),
                "answers_differ": answers_differ,
                "reason": "near_duplicate_prompt_with_different_answers" if answers_differ else "near_duplicate_prompt",
            })
    return {
        "method": "character_trigram_cosine_lexical_heuristic",
        "threshold": threshold,
        "candidate_pairs_compared": len(candidate_pairs),
        "candidate_limit": max_candidates,
        "candidate_search_truncated": truncated,
        "near_duplicate_prompt_pairs": similar_pairs,
        "near_duplicate_pairs_with_different_answers": conflicting_pairs,
        "maximum_candidate_similarity": round(max_similarity, 4),
        "review_candidates": sorted(review_candidates, key=lambda item: (-item["similarity"], item["example_id_a"], item["example_id_b"])),
        "limitation": "Lexical similarity only; not semantic understanding, paraphrase verification, or proof of duplicate content.",
        "raw_text_included": False,
    }


def audit_dataset(path: Path, context_length: int = 256, tokenizer_path: Path | None = None) -> dict:
    if context_length < 2:
        raise ValueError("context_length minimal 2")
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
    split_tasks = defaultdict(Counter)
    licenses = Counter()
    languages = Counter()
    tasks = Counter()
    approved_sources = set()
    source_example_counts = Counter()
    approved_examples = 0
    chars_by_split = defaultdict(list)
    tokens_by_split = defaultdict(list)
    user_chars_by_split = defaultdict(list)
    assistant_chars_by_split = defaultdict(list)
    eligible_token_lengths = []
    short_user_examples = 0
    short_assistant_examples = 0
    long_examples = 0
    answer_hash_counts = Counter()
    prompt_to_answers = defaultdict(set)
    prompt_hash_counts = Counter()

    for record in records:
        quality[record.get("quality_review", "missing")] += 1
        privacy[record.get("privacy_review", "missing")] += 1
        split = record.get("split", "unassigned")
        splits[split] += 1
        languages[record.get("language", "missing")] += 1
        task = record.get("task", "missing")
        tasks[task] += 1
        source = record.get("source") or {}
        licenses[source.get("license", "missing")] += 1
        source_id = source.get("source_id")
        if record.get("privacy_review") == "passed" and record.get("quality_review") == "passed":
            approved_examples += 1
            approved_sources.add(source_id)
            source_example_counts[source_id] += 1
            if split in {"train", "validation", "test"}:
                split_sources[split].add(source_id)
                split_tasks[split][task] += 1
                user_text = "\n".join(m["content"] for m in record["messages"] if m["role"] == "user")
                assistant_text = "\n".join(m["content"] for m in record["messages"] if m["role"] == "assistant")
                user_chars_by_split[split].append(len(user_text))
                assistant_chars_by_split[split].append(len(assistant_text))
                if len(user_text.strip()) < 10:
                    short_user_examples += 1
                if len(assistant_text.strip()) < 20:
                    short_assistant_examples += 1
                if len(user_text) + len(assistant_text) > 4000:
                    long_examples += 1
                prompt_hash = normalized_message_hash(record, "user")
                answer_hash = normalized_message_hash(record, "assistant")
                prompt_hash_counts[prompt_hash] += 1
                prompt_to_answers[prompt_hash].add(answer_hash)
                answer_hash_counts[answer_hash] += 1
                char_count, token_count = record_lengths(record, tokenizer)
                chars_by_split[split].append(char_count)
                if token_count is not None:
                    tokens_by_split[split].append(token_count)
                    eligible_token_lengths.append(token_count)

    near_duplicate_stats = near_duplicate_prompt_stats(records)
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
    if short_user_examples:
        warnings.append("Ada prompt user yang sangat pendek; tinjau konteks dan kegunaannya secara manual")
    if short_assistant_examples:
        warnings.append("Ada jawaban sangat pendek; pastikan bukan jawaban kosong atau tidak informatif")
    if long_examples:
        warnings.append("Ada contoh gabungan di atas 4.000 karakter; periksa kualitas dan batas konteksnya")

    repeated_answer_groups = sum(1 for count in answer_hash_counts.values() if count >= 3)
    repeated_prompt_groups = sum(1 for count in prompt_hash_counts.values() if count >= 2)
    conflicting_prompt_groups = sum(1 for answers in prompt_to_answers.values() if len(answers) > 1)
    if repeated_answer_groups:
        warnings.append("Ada jawaban identik yang dipakai pada tiga atau lebih contoh; tinjau kemungkinan pola jawaban berulang")
    if repeated_prompt_groups:
        warnings.append("Ada pertanyaan identik yang muncul berulang; tinjau apakah duplikat perlu digabung")
    if conflicting_prompt_groups:
        warnings.append("Ada pertanyaan identik dengan jawaban berbeda; tinjau konsistensi atau konteks sumber sebelum training")
    if near_duplicate_stats["near_duplicate_prompt_pairs"]:
        warnings.append("Ada pasangan pertanyaan yang mirip secara leksikal; tinjau manual sebelum training")
    if near_duplicate_stats["near_duplicate_pairs_with_different_answers"]:
        warnings.append("Ada pasangan pertanyaan mirip dengan jawaban berbeda; pastikan perbedaan konteksnya valid")
    max_source_examples = max(source_example_counts.values(), default=0)
    source_concentration = round(max_source_examples / approved_examples, 4) if approved_examples else 0.0
    if source_concentration > 0.5:
        warnings.append("Lebih dari separuh contoh berasal dari satu sumber; keragaman sumber perlu ditingkatkan")

    length_report = {
        "characters_per_example": {
            split: distribution(chars_by_split[split]) for split in ("train", "validation", "test")
        },
        "user_characters_per_example": {
            split: distribution(user_chars_by_split[split]) for split in ("train", "validation", "test")
        },
        "assistant_characters_per_example": {
            split: distribution(assistant_chars_by_split[split]) for split in ("train", "validation", "test")
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
            "note": "Panjang contoh utuh; trainer mengambil jendela konteks dan bukan membuang contoh panjang."
                    if tokenizer else "Berikan --tokenizer untuk mengukur panjang token terhadap context length.",
        },
    }
    if tokenizer and eligible_token_lengths and max(eligible_token_lengths) > context_length:
        warnings.append("Sebagian contoh lebih panjang dari context length; periksa apakah batas giliran tetap terwakili")

    return {
        "status": "blocked" if blockers else "ready_for_pilot_review",
        "records_total": len(records),
        "approved_examples": approved_examples,
        "approved_source_groups": len(approved_sources),
        "source_balance": {
            "largest_source_examples": max_source_examples,
            "largest_source_share": source_concentration,
            "source_count_by_split": {key: len(split_sources[key]) for key in ("train", "validation", "test")},
        },
        "quality_profile": {
            "short_user_examples_under_10_chars": short_user_examples,
            "short_assistant_examples_under_20_chars": short_assistant_examples,
            "long_examples_over_4000_chars": long_examples,
            "groups_of_identical_answers_repeated_at_least_3_times": repeated_answer_groups,
            "groups_of_identical_prompts_repeated_at_least_2_times": repeated_prompt_groups,
            "groups_of_identical_prompts_with_different_answers": conflicting_prompt_groups,
            "similarity_method": "exact normalized text hashes plus character-trigram lexical cosine; not semantic understanding",
            "near_duplicate_prompt_analysis": near_duplicate_stats,
            "task_counts_by_split": {
                split: dict(sorted(split_tasks[split].items())) for split in ("train", "validation", "test")
            },
        },
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
        "privacy_note": "Laporan hanya berisi statistik agregat; tidak menampilkan isi percakapan atau teks mentah.",
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
