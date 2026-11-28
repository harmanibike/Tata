"""Load only reviewed train/validation examples for Tata's local trainer.

The test split is deliberately never loaded by this module.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

try:
    from tools.corpus.train_tokenizer import CharacterBPE, SPECIAL_TOKENS
except ModuleNotFoundError:
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "corpus"))
    from train_tokenizer import CharacterBPE, SPECIAL_TOKENS


def tokenizer_vocabulary(tokenizer: CharacterBPE) -> tuple[list[str], dict[str, int]]:
    ordered = list(SPECIAL_TOKENS)
    ordered.extend(tokenizer.alphabet)
    for left, right in tokenizer.merges:
        merged = left + right
        if merged not in ordered:
            ordered.append(merged)
    return ordered, {token: index for index, token in enumerate(ordered)}


def load_reviewed_sequences(
    corpus_path: Path,
    tokenizer_path: Path,
    split: str,
) -> tuple[list[list[int]], dict[str, Any]]:
    if split not in {"train", "validation"}:
        raise ValueError("Trainer hanya boleh memuat split train atau validation")
    tokenizer = CharacterBPE.load(tokenizer_path)
    vocabulary, token_to_id = tokenizer_vocabulary(tokenizer)
    sequences: list[list[int]] = []
    source_ids: set[str] = set()
    record_count = 0
    unknown_id = token_to_id["<unk>"]
    bos_id = token_to_id["<bos>"]
    eos_id = token_to_id["<eos>"]

    with corpus_path.open("r", encoding="utf-8") as handle:
        for line_number, raw in enumerate(handle, 1):
            if not raw.strip():
                raise ValueError(f"Baris kosong tidak diizinkan: {line_number}")
            record = json.loads(raw)
            if record.get("privacy_review") != "passed" or record.get("quality_review") != "passed":
                continue
            if record.get("split") != split:
                continue
            source = record.get("source")
            if not isinstance(source, dict) or not source.get("source_id"):
                raise ValueError(f"source_id tidak valid pada baris {line_number}")
            messages = record.get("messages")
            if not isinstance(messages, list) or len(messages) < 2:
                raise ValueError(f"messages tidak valid pada baris {line_number}")
            ids = [bos_id]
            for message in messages:
                if not isinstance(message, dict) or message.get("role") not in {"user", "assistant"}:
                    raise ValueError(f"role tidak valid pada baris {line_number}")
                content = message.get("content")
                if not isinstance(content, str) or not content.strip():
                    raise ValueError(f"content kosong pada baris {line_number}")
                role_tokens = tokenizer.encode(message["role"] + ": ")
                ids.extend(token_to_id.get(token, unknown_id) for token in role_tokens)
                ids.extend(token_to_id.get(token, unknown_id) for token in tokenizer.encode(content))
            ids.append(eos_id)
            if len(ids) >= 3:
                sequences.append(ids)
                source_ids.add(source["source_id"])
                record_count += 1

    digest = hashlib.sha256(corpus_path.read_bytes()).hexdigest()
    tokenizer_digest = hashlib.sha256(tokenizer_path.read_bytes()).hexdigest()
    metadata = {
        "split": split,
        "records": record_count,
        "source_groups": len(source_ids),
        "vocab_size": len(vocabulary),
        "corpus_sha256": digest,
        "tokenizer_sha256": tokenizer_digest,
    }
    return sequences, metadata
