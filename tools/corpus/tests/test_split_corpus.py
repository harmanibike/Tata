import tempfile
import unittest
from pathlib import Path

from split_corpus import assign_splits, allocate_group_counts, write_jsonl_atomic


def record(example_id, source_id, quality="passed"):
    return {
        "example_id": example_id,
        "source": {"source_id": source_id},
        "privacy_review": "passed",
        "quality_review": quality,
        "split": "unassigned",
        "messages": [],
    }


class CorpusSplitTests(unittest.TestCase):
    def test_same_source_never_crosses_splits(self):
        records = [
            record("a1", "source-a"), record("a2", "source-a"),
            record("b1", "source-b"), record("c1", "source-c"),
            record("d1", "source-d"), record("e1", "source-e"),
        ]
        assigned, counts = assign_splits(records, seed="stable")
        by_source = {}
        for item in assigned:
            by_source.setdefault(item["source"]["source_id"], set()).add(item["split"])
        self.assertTrue(all(len(splits) == 1 for splits in by_source.values()))
        self.assertEqual({"train", "validation", "test"},
                         {item["split"] for item in assigned})
        self.assertEqual(len(records), sum(counts.values()))

    def test_assignment_is_deterministic(self):
        records = [record(str(i), f"source-{i}") for i in range(12)]
        first, _ = assign_splits(records, seed="same-seed")
        second, _ = assign_splits(records, seed="same-seed")
        self.assertEqual([x["split"] for x in first], [x["split"] for x in second])

    def test_pending_examples_remain_unassigned(self):
        records = [
            record("a", "source-a"), record("b", "source-b"),
            record("c", "source-c"), record("pending", "source-private", "pending"),
        ]
        assigned, _ = assign_splits(records)
        self.assertEqual("unassigned", assigned[-1]["split"])

    def test_requires_three_distinct_approved_sources(self):
        with self.assertRaises(ValueError):
            assign_splits([record("a", "only-source")])

    def test_rejects_invalid_ratios(self):
        with self.assertRaises(ValueError):
            allocate_group_counts(4, (0.8, 0.1, 0.2))

    def test_writes_valid_jsonl_atomically(self):
        import json
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "nested" / "out.jsonl"
            records = [record("a", "source-a")]
            write_jsonl_atomic(path, records)
            self.assertEqual(records, [json.loads(line) for line in path.read_text().splitlines()])


if __name__ == "__main__":
    unittest.main()
