import json
import tempfile
import unittest
from pathlib import Path

from build_review_queue import build_review_queue


def make_record(example_id, source_id, split="unassigned", privacy="passed", quality="pending", prompt=None):
    return {
        "schema_version": 1, "example_id": example_id,
        "source": {"source_id": source_id, "title": "Materi berizin",
                   "license": "owned_or_explicitly_permitted", "uri": None},
        "language": "id", "task": "instruction_response",
        "messages": [
            {"role": "user", "content": prompt or f"Pertanyaan unik {example_id}?"},
            {"role": "assistant", "content": f"Jawaban unik untuk {example_id}."},
        ],
        "privacy_review": privacy, "quality_review": quality, "split": split,
    }


class ReviewQueueTests(unittest.TestCase):
    def setup_corpus(self, records):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        source, output = root / "corpus.jsonl", root / "queue.json"
        source.write_text("".join(json.dumps(x, ensure_ascii=False) + "\n" for x in records), encoding="utf-8")
        return source, output

    def test_exports_pending_records_without_raw_text(self):
        records = [
            make_record("privacy-pending", "src-1", privacy="pending", quality="pending"),
            make_record("quality-pending", "src-2", privacy="passed", quality="pending"),
        ]
        source, output = self.setup_corpus(records)
        report = build_review_queue(source, output)
        queue = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(2, report["review_items"])
        self.assertEqual({"privacy-pending", "quality-pending"}, {x["example_id"] for x in queue["items"]})
        self.assertFalse(queue["raw_conversation_text_included"])
        self.assertNotIn("Pertanyaan unik", json.dumps(queue))

    def test_includes_approved_near_duplicate_candidates(self):
        records = [
            make_record("near-a", "src-a", "train", quality="passed",
                        prompt="Bagaimana cara merawat sepeda gunung?"),
            make_record("near-b", "src-b", "validation", quality="passed",
                        prompt="Bagaimana cara merawat sepeda gunung"),
        ]
        source, output = self.setup_corpus(records)
        build_review_queue(source, output)
        queue = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(2, len(queue["items"]))
        self.assertTrue(all(x["related_examples"] for x in queue["items"]))
        self.assertTrue(all("near_duplicate_prompt" in x["reasons"] for x in queue["items"]))

    def test_refuses_to_overwrite_queue(self):
        source, output = self.setup_corpus([make_record("one", "src-one")])
        output.write_text("keep", encoding="utf-8")
        with self.assertRaises(FileExistsError):
            build_review_queue(source, output)
        self.assertEqual("keep", output.read_text(encoding="utf-8"))

    def test_refuses_invalid_corpus(self):
        source, output = self.setup_corpus([make_record("one", "src-one")])
        source.write_text("{bad json}\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Korpus tidak valid"):
            build_review_queue(source, output)


if __name__ == "__main__":
    unittest.main()
