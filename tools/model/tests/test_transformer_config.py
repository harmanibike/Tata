import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from transformer_config import TransformerConfig


class TransformerConfigTests(unittest.TestCase):
    def test_baseline_dimensions_and_parameter_count(self):
        config = TransformerConfig()
        config.validate()
        self.assertEqual(config.head_dim, 32)
        self.assertEqual(config.parameter_count(), 1_350_400)

    def test_parameter_breakdown_sums_to_total(self):
        config = TransformerConfig()
        self.assertEqual(
            sum(config.parameter_breakdown().values()),
            config.parameter_count(),
        )

    def test_untied_head_adds_embedding_matrix(self):
        tied = TransformerConfig()
        untied = TransformerConfig(tie_embeddings=False)
        self.assertEqual(
            untied.parameter_count() - tied.parameter_count(),
            untied.vocab_size * untied.hidden_size + untied.vocab_size,
        )

    def test_bias_free_configuration(self):
        config = TransformerConfig(use_bias=False)
        self.assertGreater(config.parameter_count(), 0)
        self.assertEqual(config.parameter_breakdown()["untied_lm_head"], 0)

    def test_rejects_non_divisible_attention_dimensions(self):
        with self.assertRaisesRegex(ValueError, "habis dibagi"):
            TransformerConfig(hidden_size=130, num_heads=4).validate()

    def test_rejects_invalid_dimensions(self):
        with self.assertRaises(ValueError):
            TransformerConfig(num_layers=0).validate()
        with self.assertRaises(ValueError):
            TransformerConfig(context_length=1).validate()
        with self.assertRaises(ValueError):
            TransformerConfig(intermediate_size=64).validate()

    def test_report_explicitly_says_no_training(self):
        report = TransformerConfig().report()
        self.assertEqual(
            report["status"],
            "configuration_only_no_weights_no_training",
        )


if __name__ == "__main__":
    unittest.main()
