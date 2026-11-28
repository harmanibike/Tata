#!/usr/bin/env python3
"""Evaluate a Tata checkpoint on approved held-out test data only."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "corpus"))
sys.path.insert(0, str(ROOT / "tools" / "model"))

from train_tokenizer import CharacterBPE, SPECIAL_TOKENS
from validate_corpus import validate_file
from transformer_config import TransformerConfig
from train_transformer import build_model_class


ALLOWED_LICENSES = {
    "public_domain", "cc0", "cc_by", "cc_by_sa",
    "owned_or_explicitly_permitted", "explicit_permission",
}


def load_test_sequences(corpus_path: Path, tokenizer_path: Path):
    errors, _ = validate_file(corpus_path)
    if errors:
        raise ValueError("Korpus tidak lolos validasi: " + "; ".join(errors[:5]))
    tokenizer = CharacterBPE.load(tokenizer_path)
    vocabulary = list(SPECIAL_TOKENS) + list(tokenizer.alphabet)
    for left, right in tokenizer.merges:
        merged = left + right
        if merged not in vocabulary:
            vocabulary.append(merged)
    token_to_id = {token: index for index, token in enumerate(vocabulary)}
    unknown_id = token_to_id["<unk>"]
    sequences, sources = [], set()
    with corpus_path.open("r", encoding="utf-8") as handle:
        for line_number, raw in enumerate(handle, 1):
            if not raw.strip():
                raise ValueError(f"Baris kosong pada baris {line_number}")
            record = json.loads(raw)
            if record.get("split") != "test":
                continue
            if record.get("privacy_review") != "passed" or record.get("quality_review") != "passed":
                raise ValueError(f"Contoh test belum lolos review pada baris {line_number}")
            source = record.get("source")
            if (
                not isinstance(source, dict)
                or not source.get("source_id")
                or source.get("license") not in ALLOWED_LICENSES
            ):
                raise ValueError(f"Sumber/lisensi test tidak valid pada baris {line_number}")
            messages = record.get("messages")
            if not isinstance(messages, list) or len(messages) < 2:
                raise ValueError(f"Messages tidak valid pada baris {line_number}")
            ids = [token_to_id["<bos>"]]
            for message in messages:
                if not isinstance(message, dict) or message.get("role") not in {"user", "assistant"}:
                    raise ValueError(f"Role tidak valid pada baris {line_number}")
                content = message.get("content")
                if not isinstance(content, str) or not content.strip():
                    raise ValueError(f"Content kosong pada baris {line_number}")
                for token in tokenizer.encode(message["role"] + ": " + content + "\n"):
                    ids.append(token_to_id.get(token, unknown_id))
            ids.append(token_to_id["<eos>"])
            if len(ids) > 1:
                sequences.append(ids)
                sources.add(source["source_id"])
    metadata = {
        "records": len(sequences),
        "source_groups": len(sources),
        "corpus_sha256": hashlib.sha256(corpus_path.read_bytes()).hexdigest(),
        "tokenizer_sha256": hashlib.sha256(tokenizer_path.read_bytes()).hexdigest(),
    }
    return tokenizer, vocabulary, sequences, metadata


def score_sequences(torch, model, sequences, config, device):
    total_loss = 0.0
    total_tokens = 0
    model.eval()
    with torch.no_grad():
        for sequence in sequences:
            for start in range(0, len(sequence) - 1, config.context_length):
                window = sequence[start:start + config.context_length + 1]
                if len(window) < 2:
                    continue
                x = torch.tensor([window[:-1]], dtype=torch.long, device=device)
                y = torch.tensor([window[1:]], dtype=torch.long, device=device)
                logits = model(x)
                token_count = y.numel()
                loss = torch.nn.functional.cross_entropy(
                    logits.reshape(-1, config.vocab_size),
                    y.reshape(-1),
                    reduction="sum",
                )
                total_loss += float(loss.item())
                total_tokens += token_count
    if total_tokens == 0:
        raise ValueError("Test set tidak memiliki token yang dapat dievaluasi")
    mean_loss = total_loss / total_tokens
    return {
        "test_loss": mean_loss,
        "test_perplexity": math.exp(min(mean_loss, 20)),
        "evaluated_tokens": total_tokens,
    }


def generate_sample(torch, model, tokenizer, token_to_id, prompt, config, device, max_new_tokens):
    ids = [token_to_id["<bos>"]]
    for token in tokenizer.encode("user: " + prompt + "\nassistant: "):
        ids.append(token_to_id.get(token, token_to_id["<unk>"]))
    ids = ids[-config.context_length:]
    generated_ids = []
    model.eval()
    with torch.no_grad():
        for _ in range(max_new_tokens):
            input_ids = torch.tensor([ids], dtype=torch.long, device=device)
            logits = model(input_ids)[0, -1]
            next_id = int(torch.argmax(logits).item())
            if next_id == token_to_id["<eos>"]:
                break
            ids.append(next_id)
            generated_ids.append(next_id)
            if len(ids) > config.context_length:
                ids = ids[-config.context_length:]
    id_to_token = {value: key for key, value in token_to_id.items()}
    return tokenizer.decode(id_to_token.get(token_id, "<unk>") for token_id in generated_ids)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--corpus", type=Path, required=True)
    parser.add_argument("--tokenizer", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--device", choices=("auto", "cpu", "cuda"), default="auto")
    parser.add_argument("--prompt", action="append", default=[])
    parser.add_argument("--max-new-tokens", type=int, default=64)
    args = parser.parse_args()
    if args.max_new_tokens < 1:
        parser.error("max-new-tokens harus positif")
    try:
        import torch
    except ImportError:
        parser.error("PyTorch 2.x belum terpasang pada komputer evaluasi")
    if args.device == "cuda" and not torch.cuda.is_available():
        parser.error("CUDA diminta tetapi tidak tersedia")
    device = ("cuda" if torch.cuda.is_available() else "cpu") if args.device == "auto" else args.device

    checkpoint = torch.load(args.checkpoint, map_location=device, weights_only=False)
    if checkpoint.get("format") != "tata-transformer-checkpoint":
        parser.error("Format checkpoint bukan Tata Transformer")
    if checkpoint.get("training", {}).get("pretrained_weights") is not False:
        parser.error("Checkpoint bukan hasil inisialisasi bobot acak yang diharapkan")
    config_data = checkpoint.get("config", {}).get("config")
    if not isinstance(config_data, dict):
        parser.error("Konfigurasi checkpoint tidak lengkap")
    config = TransformerConfig(**config_data)
    config.validate()

    tokenizer, vocabulary, sequences, test_meta = load_test_sequences(args.corpus, args.tokenizer)
    if not sequences:
        parser.error("Tidak ada contoh test yang lulus review")
    if len(vocabulary) != config.vocab_size:
        parser.error("Ukuran vocabulary tokenizer tidak cocok dengan checkpoint")
    if checkpoint.get("train_meta", {}).get("tokenizer_sha256") != test_meta["tokenizer_sha256"]:
        parser.error("Tokenizer test berbeda dari tokenizer yang digunakan saat training")
    model = build_model_class(torch)(config).to(device)
    model.load_state_dict(checkpoint["model"])
    metrics = score_sequences(torch, model, sequences, config, device)

    token_to_id = {token: index for index, token in enumerate(vocabulary)}
    generations = []
    for prompt in args.prompt:
        generations.append({
            "prompt": prompt,
            "generated_text": generate_sample(
                torch, model, tokenizer, token_to_id, prompt,
                config, device, args.max_new_tokens
            ),
        })
    report = {
        "format": "tata-transformer-evaluation",
        "format_version": 1,
        "checkpoint_step": checkpoint.get("step"),
        "parameters": sum(parameter.numel() for parameter in model.parameters()),
        "test": test_meta,
        "metrics": metrics,
        "generations": generations,
        "limitations": [
            "Perplexity bukan ukuran langsung kebenaran, keamanan, atau kualitas percakapan.",
            "Generasi contoh harus ditinjau manusia untuk halusinasi, relevansi, bahasa, bias, dan privasi.",
            "Evaluasi ini tidak memuat atau melatih ulang data test.",
        ],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
