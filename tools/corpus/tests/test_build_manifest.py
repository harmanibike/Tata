import json
import tempfile
import unittest
from pathlib import Path

from build_manifest import create_manifest


def record(example_id="ex-1", split="train"):
    return {
        "schema_version": 1,
        "example_id": example_id,
        "source": {
            "source_id": "source-1",
            "title": "Materi milik sendiri",
            "license": "owned_or_explicitly_permitted",
            "uri": None,
        },
        "language": "id",
        "task": "instruction_response",
        "messages": [
            {"role": "user", "content": "Apa itu energi?"},
            {"role": "assistant", "content": "Energi adalah kemampuan melakukan usaha."},
        ],
        "privacy_review": "passed",
        "quality_review": "passed",
        "split": split,
    }


class ManifestTests(unittest.TestCase):
    def test_manifest_contains_hash_source_provenance_and_split_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "corpus.jsonl"
            second = record("ex-2", "validation")
            second["messages"][0]["content"] = "Bagaimana energi digunakan?"
            second["messages"][1]["content"] = "Energi dapat digunakan untuk melakukan kerja."
            path.write_text(
                json.dumps(record(), ensure_ascii=False) + "\n"
                + json.dumps(second, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            manifest = create_manifest(path)
        self.assertEqual(2, manifest["record_count"])
        self.assertEqual(1, manifest["source_count"])
        self.assertEqual({"train": 1, "validation": 1}, manifest["split_counts"])
        self.assertEqual("passed", manifest["validation"]["status"])
        self.assertEqual(64, len(manifest["corpus_sha256"]))

    def test_manifest_refuses_invalid_corpus(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "corpus.jsonl"
            invalid = record()
            invalid["privacy_review"] = "pending"
            path.write_text(json.dumps(invalid), encoding="utf-8")
            with self.assertRaises(ValueError):
                create_manifest(path)


if __name__ == "__main__":
    unittest.main()
