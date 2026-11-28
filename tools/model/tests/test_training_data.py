from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.corpus.train_tokenizer import CharacterBPE
from tools.model.training_data import load_reviewed_sequences, tokenizer_vocabulary


class TrainingDataTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.corpus = self.root / "corpus.jsonl"
        self.tokenizer_path = self.root / "tokenizer.json"
        self.tokenizer = CharacterBPE.train(["user: halo tata assistant: halo"], vocab_size=64)
        self.tokenizer.save(self.tokenizer_path)

    def tearDown(self):
        self.temp.cleanup()

    def write_records(self, records):
        self.corpus.write_text(
            "".join(json.dumps(item, ensure_ascii=False) + "\n" for item in records),
            encoding="utf-8",
        )

    @staticmethod
    def record(example_id, split, quality="passed"):
        return {
            "schema_version": 1,
            "example_id": example_id,
            "source": {"source_id": "source-" + example_id, "title": "Test", "license": "cc0"},
            "language": "id",
            "task": "conversation",
            "privacy_review": "passed",
            "quality_review": quality,
            "split": split,
            "messages": [
                {"role": "user", "content": "Halo Tata"},
                {"role": "assistant", "content": "Halo, ada yang bisa dibantu?"},
            ],
        }

    def test_only_requested_approved_split_is_loaded(self):
        self.write_records([
            self.record("train", "train"),
            self.record("validation", "validation"),
            self.record("pending", "train", "pending"),
            self.record("test", "test"),
        ])
        sequences, metadata = load_reviewed_sequences(
            self.corpus, self.tokenizer_path, "train"
        )
        self.assertEqual(metadata["records"], 1)
        self.assertEqual(len(sequences), 1)

    def test_test_split_is_never_loadable(self):
        with self.assertRaises(ValueError):
            load_reviewed_sequences(self.corpus, self.tokenizer_path, "test")

    def test_tokenizer_mapping_is_deterministic(self):
        first = tokenizer_vocabulary(self.tokenizer)
        second = tokenizer_vocabulary(self.tokenizer)
        self.assertEqual(first, second)
        self.assertEqual(len(first[0]), len(first[1]))

    def test_invalid_message_is_rejected(self):
        record = self.record("bad", "train")
        record["messages"][0]["role"] = "system"
        self.write_records([record])
        with self.assertRaises(ValueError):
            load_reviewed_sequences(self.corpus, self.tokenizer_path, "train")


if __name__ == "__main__":
    unittest.main()
