import json
import tempfile
import unittest
from pathlib import Path

from audit_dataset import audit_dataset


def make_record(example_id, source_id, split, quality="passed"):
    return {
        "schema_version": 1,
        "example_id": example_id,
        "source": {
            "source_id": source_id,
            "title": "Materi contoh",
            "license": "public_domain",
            "uri": None,
        },
        "language": "id",
        "task": "instruction_response",
        "messages": [
            {"role": "user", "content": f"Pertanyaan {example_id}?"},
            {"role": "assistant", "content": f"Jawaban contoh {example_id}."},
        ],
        "privacy_review": "passed",
        "quality_review": quality,
        "split": split,
    }


class DatasetAuditTests(unittest.TestCase):
    def write_records(self, records):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "corpus.jsonl"
        path.write_text(
            "".join(json.dumps(item, ensure_ascii=False) + "\n" for item in records),
            encoding="utf-8",
        )
        return path

    def test_empty_approved_corpus_is_blocked_without_text_leak(self):
        path = Path(__file__).resolve().parents[1] / "examples" / "synthetic_sample.jsonl"
        report = audit_dataset(path)
        self.assertEqual("blocked", report["status"])
        self.assertEqual(0, report["approved_examples"])
        self.assertNotIn("messages", report)
        self.assertNotIn("Pertanyaan", json.dumps(report))

    def test_three_approved_source_groups_across_splits_ready(self):
        records = [
            make_record("one", "src-one", "train"),
            make_record("two", "src-two", "validation"),
            make_record("three", "src-three", "test"),
        ]
        report = audit_dataset(self.write_records(records), context_length=64)
        self.assertEqual("ready_for_pilot_review", report["status"])
        self.assertEqual(3, report["approved_source_groups"])
        self.assertEqual(1, report["split_source_groups"]["validation"])

    def test_pending_quality_does_not_count_as_approved(self):
        records = [
            make_record("one", "src-one", "unassigned", "pending"),
            make_record("two", "src-two", "unassigned", "pending"),
            make_record("three", "src-three", "unassigned", "pending"),
        ]
        report = audit_dataset(self.write_records(records))
        self.assertEqual("blocked", report["status"])
        self.assertEqual(0, report["approved_examples"])


    def test_length_statistics_and_tokenizer_metrics_are_aggregated(self):
        from train_tokenizer import CharacterBPE
        records = [
            make_record("one", "src-one", "train"),
            make_record("two", "src-two", "validation"),
            make_record("three", "src-three", "test"),
        ]
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        tokenizer_path = Path(temporary.name) / "tokenizer.json"
        tokenizer = CharacterBPE.train(
            ["user: Pertanyaan contoh?\\n", "assistant: Jawaban contoh.\\n"],
            vocab_size=64,
            min_frequency=1,
        )
        tokenizer.save(tokenizer_path)
        report = audit_dataset(
            self.write_records(records), context_length=64, tokenizer_path=tokenizer_path
        )
        self.assertTrue(report["length_analysis"]["tokenizer_supplied"])
        self.assertEqual(1, report["length_analysis"]["tokens_per_example"]["train"]["count"])
        self.assertIsNotNone(report["length_analysis"]["characters_per_example"]["train"]["median"])
        self.assertNotIn("Pertanyaan one", json.dumps(report))

    def test_invalid_corpus_is_reported_as_invalid(self):
        path = self.write_records([{"schema_version": 1}])
        report = audit_dataset(path)
        self.assertEqual("invalid", report["status"])
        self.assertGreater(report["error_count"], 0)


if __name__ == "__main__":
    unittest.main()
