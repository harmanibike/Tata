import tempfile
import unittest
from pathlib import Path

import json

from train_tokenizer import CharacterBPE, load_training_texts


class CharacterBPETests(unittest.TestCase):
    def test_round_trip_indonesian_text(self):
        texts = [
            "Tata belajar Bahasa Indonesia.",
            "Belajar bahasa Indonesia bersama Tata.",
            "Angka 10.000 dan kata berimbuhan membaca."
        ]
        tokenizer = CharacterBPE.train(texts, vocab_size=128, min_frequency=2)
        sample = "Tata belajar Bahasa Indonesia."
        self.assertEqual(sample, tokenizer.decode(tokenizer.encode(sample)))

    def test_training_is_deterministic(self):
        texts = ["membaca buku", "membaca cerita", "buku cerita"]
        first = CharacterBPE.train(texts, vocab_size=64, min_frequency=2)
        second = CharacterBPE.train(texts, vocab_size=64, min_frequency=2)
        self.assertEqual(first.to_dict(), second.to_dict())

    def test_unknown_character_uses_unknown_token(self):
        tokenizer = CharacterBPE.train(["tata ai"], vocab_size=32)
        self.assertIn("<unk>", tokenizer.encode("tata 🧩"))

    def test_save_and_load_preserves_encoding(self):
        tokenizer = CharacterBPE.train(["kata-kata sederhana", "kata yang sederhana"], vocab_size=64)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "tokenizer.json"
            tokenizer.save(path)
            loaded = CharacterBPE.load(path)
        self.assertEqual(tokenizer.encode("kata sederhana"), loaded.encode("kata sederhana"))
        self.assertEqual(tokenizer.to_dict(), loaded.to_dict())

    def test_rejects_too_small_vocabulary(self):
        with self.assertRaises(ValueError):
            CharacterBPE.train(["Indonesia"], vocab_size=4)

    def test_training_loader_excludes_unreviewed_and_held_out_records(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "corpus.jsonl"
            base = {
                "privacy_review": "passed",
                "quality_review": "passed",
                "split": "train",
                "source": {"source_id": "sumber-1", "license": "cc0"},
                "messages": [
                    {"role": "user", "content": "teks latih"},
                    {"role": "assistant", "content": "jawaban latih"},
                ],
            }
            pending = {**base, "quality_review": "pending"}
            held_out = {**base, "split": "test"}
            unassigned = {**base, "split": "unassigned"}
            path.write_text(
                "\n".join(json.dumps(item) for item in [base, pending, held_out, unassigned]),
                encoding="utf-8",
            )
            self.assertEqual(
                ["user: teks latih", "assistant: jawaban latih"],
                load_training_texts(path),
            )

    def test_training_loader_rejects_unapproved_license(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "corpus.jsonl"
            record = {
                "privacy_review": "passed",
                "quality_review": "passed",
                "split": "train",
                "source": {"source_id": "sumber", "license": "unknown"},
                "messages": [
                    {"role": "user", "content": "pertanyaan"},
                    {"role": "assistant", "content": "jawaban"},
                ],
            }
            path.write_text(json.dumps(record), encoding="utf-8")
            with self.assertRaises(ValueError):
                load_training_texts(path)


if __name__ == "__main__":
    unittest.main()
