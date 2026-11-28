import tempfile
import unittest
from pathlib import Path

import json

from train_tokenizer import CharacterBPE, load_training_texts


def tokenizer_vocabulary_for_test(tokenizer):
    return list(tokenizer.vocab)


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

    def test_merge_vocabulary_contains_only_unique_tokens(self):
        tokenizer = CharacterBPE.train(
            ["kata kata kata", "kata-kata kata"], vocab_size=64, min_frequency=1
        )
        self.assertEqual(len(tokenizer.vocab), len(tokenizer_vocabulary_for_test(tokenizer)))
        payload = tokenizer.to_dict()
        restored = CharacterBPE.from_dict(payload)
        self.assertEqual(tokenizer.to_dict(), restored.to_dict())

    def test_rejects_duplicate_alphabet_entries(self):
        payload = CharacterBPE.train(["tata"], vocab_size=32).to_dict()
        payload["alphabet"].append(payload["alphabet"][0])
        with self.assertRaises(ValueError):
            CharacterBPE.from_dict(payload)

    def test_rejects_merge_using_unknown_symbol(self):
        payload = CharacterBPE.train(["tata"], vocab_size=32).to_dict()
        payload["merges"] = [["tidak-ada", "t"]]
        payload["vocab_size"] = len(set(payload["special_tokens"] + payload["alphabet"])) + 1
        with self.assertRaises(ValueError):
            CharacterBPE.from_dict(payload)

    def test_rejects_incorrect_declared_vocabulary_size(self):
        payload = CharacterBPE.train(["tata"], vocab_size=32).to_dict()
        payload["vocab_size"] += 1
        with self.assertRaises(ValueError):
            CharacterBPE.from_dict(payload)

    def test_training_loader_excludes_unreviewed_and_held_out_records(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "corpus.jsonl"
            base = {
                "schema_version": 1,
                "example_id": "latih-1",
                "language": "id",
                "task": "conversation",
                "privacy_review": "passed",
                "quality_review": "passed",
                "split": "train",
                "source": {"source_id": "sumber-1", "title": "Sumber uji", "license": "cc0"},
                "messages": [
                    {"role": "user", "content": "teks latih"},
                    {"role": "assistant", "content": "jawaban latih"},
                ],
            }
            pending = {**base, "example_id": "pending-1", "quality_review": "pending",
                       "source": {"source_id": "sumber-2", "title": "Sumber uji", "license": "cc0"},
                       "messages": [{"role": "user", "content": "teks pending"},
                                    {"role": "assistant", "content": "jawaban pending"}]}
            held_out = {**base, "example_id": "test-1", "split": "test",
                        "source": {"source_id": "sumber-3", "title": "Sumber uji", "license": "cc0"},
                        "messages": [{"role": "user", "content": "teks test"},
                                     {"role": "assistant", "content": "jawaban test"}]}
            unassigned = {**base, "example_id": "unassigned-1", "split": "unassigned",
                          "source": {"source_id": "sumber-4", "title": "Sumber uji", "license": "cc0"},
                          "messages": [{"role": "user", "content": "teks belum dibagi"},
                                       {"role": "assistant", "content": "jawaban belum dibagi"}]}
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
                "schema_version": 1,
                "example_id": "lisensi-buruk",
                "language": "id",
                "task": "conversation",
                "privacy_review": "passed",
                "quality_review": "passed",
                "split": "train",
                "source": {"source_id": "sumber", "title": "Sumber uji", "license": "unknown"},
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
