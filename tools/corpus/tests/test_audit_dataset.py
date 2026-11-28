import json
import tempfile
import unittest
from pathlib import Path

from audit_dataset import audit_dataset


def make_record(example_id, source_id, split, quality="passed", user_text=None, answer_text=None, task="instruction_response"):
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
        "task": task,
        "messages": [
            {"role": "user", "content": user_text or f"Pertanyaan {example_id}?"},
            {"role": "assistant", "content": answer_text or f"Jawaban contoh {example_id}."},
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
            ["user: Pertanyaan contoh?\n", "assistant: Jawaban contoh.\n"],
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

    def test_quality_profile_flags_short_and_repeated_answers(self):
        records = [
            make_record("one", "src-one", "train", user_text="Hai", answer_text="Ya"),
            make_record("two", "src-two", "validation", user_text="Halo", answer_text="Ya"),
            make_record("three", "src-three", "test", user_text="Pagi", answer_text="Ya"),
        ]
        report = audit_dataset(self.write_records(records))
        profile = report["quality_profile"]
        self.assertEqual(3, profile["short_user_examples_under_10_chars"])
        self.assertEqual(3, profile["short_assistant_examples_under_20_chars"])
        self.assertEqual(1, profile["groups_of_identical_answers_repeated_at_least_3_times"])
        self.assertTrue(any("jawaban identik" in warning for warning in report["warnings"]))

    def test_identical_prompts_with_different_answers_are_flagged(self):
        records = [
            make_record("one", "src-one", "train", user_text="Apa itu model bahasa?", answer_text="Model statistik."),
            make_record("two", "src-two", "validation", user_text="Apa itu model bahasa?", answer_text="Sistem pemroses bahasa."),
            make_record("three", "src-three", "test", user_text="Bagaimana cara kerja model?", answer_text="Model memprediksi token."),
        ]
        report = audit_dataset(self.write_records(records))
        profile = report["quality_profile"]
        self.assertEqual(1, profile["groups_of_identical_prompts_repeated_at_least_2_times"])
        self.assertEqual(1, profile["groups_of_identical_prompts_with_different_answers"])
        self.assertIn("not semantic", profile["similarity_method"])


    def test_near_duplicate_prompt_detector_flags_typo_variants_without_text_leak(self):
        records = [
            make_record("one", "src-one", "train", user_text="Bagaimana cara merawat sepeda gunung?", answer_text="Periksa rantai dan rem."),
            make_record("two", "src-two", "validation", user_text="Bagaimana cara merawat sepeda gunung", answer_text="Bersihkan dan lumasi rantai."),
            make_record("three", "src-three", "test", user_text="Apa manfaat olahraga pagi?", answer_text="Menjaga kebugaran."),
        ]
        report = audit_dataset(self.write_records(records))
        near = report["quality_profile"]["near_duplicate_prompt_analysis"]
        self.assertGreaterEqual(near["near_duplicate_prompt_pairs"], 1)
        self.assertGreaterEqual(near["near_duplicate_pairs_with_different_answers"], 1)
        self.assertFalse(near["raw_text_included"])
        self.assertNotIn("merawat sepeda", json.dumps(report))

    def test_near_duplicate_review_queue_contains_ids_not_raw_text(self):
        records = [
            make_record("bike-a", "src-one", "train", user_text="Bagaimana cara merawat sepeda gunung?", answer_text="Periksa rem dan rantai."),
            make_record("bike-b", "src-two", "validation", user_text="Bagaimana cara merawat sepeda gunung", answer_text="Cek tekanan ban."),
            make_record("other", "src-three", "test", user_text="Apa manfaat membaca buku?", answer_text="Menambah wawasan."),
        ]
        report = audit_dataset(self.write_records(records))
        candidates = report["quality_profile"]["near_duplicate_prompt_analysis"]["review_candidates"]
        self.assertTrue(candidates)
        self.assertEqual({"bike-a", "bike-b"}, {candidates[0]["example_id_a"], candidates[0]["example_id_b"]})
        self.assertTrue(candidates[0]["answers_differ"])
        self.assertNotIn("merawat sepeda", json.dumps(candidates))

    def test_near_duplicate_analysis_excludes_unreviewed_examples(self):
        records = [
            make_record("one", "src-one", "train", user_text="Pertanyaan yang sama persis."),
            make_record("two", "src-two", "validation", quality="pending", user_text="Pertanyaan yang sama persis."),
            make_record("three", "src-three", "test", user_text="Pertanyaan lain."),
        ]
        report = audit_dataset(self.write_records(records))
        near = report["quality_profile"]["near_duplicate_prompt_analysis"]
        self.assertEqual(0, near["near_duplicate_prompt_pairs"])

    def test_source_concentration_and_task_balance_are_reported(self):
        records = [
            make_record("one", "src-one", "train", task="qa"),
            make_record("two", "src-one", "train", task="qa"),
            make_record("three", "src-two", "validation", task="reasoning"),
            make_record("four", "src-three", "test", task="summarization"),
        ]
        report = audit_dataset(self.write_records(records))
        self.assertEqual(0.5, report["source_balance"]["largest_source_share"])
        self.assertEqual({"qa": 2}, report["quality_profile"]["task_counts_by_split"]["train"])

    def test_invalid_context_length_rejected(self):
        path = self.write_records([
            make_record("one", "src-one", "train"),
            make_record("two", "src-two", "validation"),
            make_record("three", "src-three", "test"),
        ])
        with self.assertRaises(ValueError):
            audit_dataset(path, context_length=1)

    def test_invalid_corpus_is_reported_as_invalid(self):
        path = self.write_records([{"schema_version": 1}])
        report = audit_dataset(path)
        self.assertEqual("invalid", report["status"])
        self.assertGreater(report["error_count"], 0)


if __name__ == "__main__":
    unittest.main()
