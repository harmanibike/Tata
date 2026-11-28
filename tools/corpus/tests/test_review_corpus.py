import json
import tempfile
import unittest
from pathlib import Path

from review_corpus import apply_reviews


def record(example_id="ex-1", privacy="passed", quality="pending"):
    return {
        "schema_version": 1, "example_id": example_id,
        "source": {"source_id": "source-1", "title": "Owned material",
                   "license": "owned_or_explicitly_permitted", "uri": None},
        "language": "id", "task": "instruction_response",
        "messages": [{"role": "user", "content": "Apa itu fotosintesis " + example_id + "?"},
                     {"role": "assistant", "content": "Proses tumbuhan membuat makanan " + example_id + "."}],
        "privacy_review": privacy, "quality_review": quality, "split": "unassigned",
    }


class CorpusReviewTests(unittest.TestCase):
    def setup_files(self, records, decisions):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        corpus, decision_file, output = root / "source.jsonl", root / "decisions.json", root / "reviewed.jsonl"
        corpus.write_text("".join(json.dumps(x, ensure_ascii=False) + "\n" for x in records), encoding="utf-8")
        decision_file.write_text(json.dumps({"decisions": decisions}, ensure_ascii=False), encoding="utf-8")
        return corpus, decision_file, output

    def test_approve_and_reject_write_copy_and_keep_source_unchanged(self):
        records = [record("approve-me"), record("reject-me")]
        decisions = [
            {"example_id": "approve-me", "action": "approve", "reviewer": "reviewer-a"},
            {"example_id": "reject-me", "action": "reject", "reviewer": "reviewer-a"},
        ]
        corpus, decision_file, output = self.setup_files(records, decisions)
        original = corpus.read_text(encoding="utf-8")
        report = apply_reviews(corpus, decision_file, output)
        result = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
        self.assertFalse(report["source_modified"])
        self.assertEqual(original, corpus.read_text(encoding="utf-8"))
        self.assertEqual(["passed", "rejected"], [x["quality_review"] for x in result])
        self.assertEqual("approve", result[0]["review_history"][0]["action"])

    def test_correction_resets_both_reviews_and_split(self):
        messages = [{"role": "user", "content": "Apa itu fotosintesis?"},
                    {"role": "assistant", "content": "Proses biologis pada tumbuhan."}]
        decisions = [{"example_id": "ex-1", "action": "correct", "reviewer": "reviewer-a", "messages": messages}]
        corpus, decision_file, output = self.setup_files([record()], decisions)
        apply_reviews(corpus, decision_file, output)
        result = json.loads(output.read_text(encoding="utf-8").splitlines()[0])
        self.assertEqual("pending", result["privacy_review"])
        self.assertEqual("pending", result["quality_review"])
        self.assertEqual("unassigned", result["split"])
        self.assertEqual(messages, result["messages"])

    def test_approval_requires_privacy_review_passed(self):
        corpus, decision_file, output = self.setup_files(
            [record(privacy="pending")],
            [{"example_id": "ex-1", "action": "approve", "reviewer": "reviewer-a"}],
        )
        with self.assertRaisesRegex(ValueError, "privacy_review passed"):
            apply_reviews(corpus, decision_file, output)
        self.assertFalse(output.exists())

    def test_rejects_duplicate_or_unknown_decision_ids(self):
        corpus, decision_file, output = self.setup_files(
            [record()], [
                {"example_id": "ex-1", "action": "reject", "reviewer": "r"},
                {"example_id": "ex-1", "action": "approve", "reviewer": "r"},
            ],
        )
        with self.assertRaisesRegex(ValueError, "Keputusan ganda"):
            apply_reviews(corpus, decision_file, output)

    def test_refuses_to_overwrite_output(self):
        corpus, decision_file, output = self.setup_files(
            [record()], [{"example_id": "ex-1", "action": "approve", "reviewer": "r"}],
        )
        output.write_text("keep", encoding="utf-8")
        with self.assertRaises(FileExistsError):
            apply_reviews(corpus, decision_file, output)
        self.assertEqual("keep", output.read_text(encoding="utf-8"))

    def test_corrected_private_data_is_rejected(self):
        messages = [{"role": "user", "content": "Hubungi saya 081234567890"},
                    {"role": "assistant", "content": "Baik, saya mengerti."}]
        corpus, decision_file, output = self.setup_files(
            [record()], [{"example_id": "ex-1", "action": "correct", "reviewer": "r", "messages": messages}],
        )
        with self.assertRaisesRegex(ValueError, "nomor telepon"):
            apply_reviews(corpus, decision_file, output)
        self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
