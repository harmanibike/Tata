#!/usr/bin/env python3
"""Validasi konfigurasi Transformer Tata dan hitung parameter secara analitis.

Tidak memuat framework ML, tidak membuat bobot, dan tidak menjalankan training.
"""
from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass(frozen=True)
class TransformerConfig:
    vocab_size: int = 4096
    hidden_size: int = 128
    num_layers: int = 4
    num_heads: int = 4
    intermediate_size: int = 512
    context_length: int = 256
    tie_embeddings: bool = True
    use_bias: bool = True

    @property
    def head_dim(self) -> int:
        return self.hidden_size // self.num_heads

    def validate(self) -> None:
        positive = (
            "vocab_size", "hidden_size", "num_layers", "num_heads",
            "intermediate_size", "context_length",
        )
        for name in positive:
            if getattr(self, name) <= 0:
                raise ValueError(f"{name} harus lebih besar dari nol")
        if self.hidden_size % self.num_heads:
            raise ValueError("hidden_size harus habis dibagi num_heads")
        if self.intermediate_size < self.hidden_size:
            raise ValueError("intermediate_size tidak boleh lebih kecil dari hidden_size")
        if self.vocab_size < 8:
            raise ValueError("vocab_size terlalu kecil untuk eksperimen bahasa")
        if self.context_length < 2:
            raise ValueError("context_length minimal 2 token")

    def parameter_breakdown(self) -> dict[str, int]:
        self.validate()
        d = self.hidden_size
        f = self.intermediate_size
        v = self.vocab_size
        c = self.context_length
        bias = 1 if self.use_bias else 0

        token_embedding = v * d
        position_embedding = c * d
        # Q, K, V projections and attention output projection.
        attention = 4 * d * d + (4 * d * bias)
        # Two linear layers in the MLP.
        mlp = 2 * d * f + (f + d) * bias
        # Two pre-norm LayerNorms per block, each with scale and bias.
        block_norms = 4 * d
        per_block = attention + mlp + block_norms
        final_norm = 2 * d
        lm_head = 0 if self.tie_embeddings else v * d
        if not self.tie_embeddings and self.use_bias:
            lm_head += v

        return {
            "token_embedding": token_embedding,
            "position_embedding": position_embedding,
            "transformer_blocks": self.num_layers * per_block,
            "final_norm": final_norm,
            "untied_lm_head": lm_head,
        }

    def parameter_count(self) -> int:
        return sum(self.parameter_breakdown().values())

    def report(self) -> dict:
        self.validate()
        return {
            "format": "tata-transformer-config",
            "format_version": 1,
            "config": asdict(self),
            "derived": {
                "head_dim": self.head_dim,
                "parameter_breakdown": self.parameter_breakdown(),
                "total_parameters": self.parameter_count(),
            },
            "status": "configuration_only_no_weights_no_training",
        }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, help="JSON konfigurasi opsional")
    parser.add_argument("--output", type=Path, help="simpan laporan JSON")
    args = parser.parse_args()
    try:
        values = json.loads(args.config.read_text(encoding="utf-8")) if args.config else {}
        config = TransformerConfig(**values)
        report = config.report()
        rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
        if args.output:
            args.output.write_text(rendered, encoding="utf-8")
        print(rendered, end="")
    except (OSError, UnicodeError, json.JSONDecodeError, TypeError, ValueError) as exc:
        parser.error(str(exc))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
