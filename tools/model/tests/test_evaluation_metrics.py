import math
import unittest

from evaluate_transformer import score_sequences


class FakeTensor:
    def __init__(self, values):
        self.values = values

    def reshape(self, *shape):
        return self


class FakeModel:
    def eval(self):
        return self


class FakeNoGrad:
    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


class FakeFunctional:
    @staticmethod
    def cross_entropy(logits, targets, reduction="sum"):
        class Loss:
            def item(self):
                return math.log(2) * len(targets.values)
        return Loss()


class FakeTorch:
    no_grad = FakeNoGrad
    class nn:
        functional = FakeFunctional

    @staticmethod
    def tensor(values, dtype=None, device=None):
        return FakeTensor(values)


class FakeConfig:
    context_length = 4
    vocab_size = 10


class FakeLogits:
    def __getitem__(self, key):
        return self


class FakeEvalModel(FakeModel):
    def __call__(self, input_ids):
        return FakeLogits()


class EvaluationMetricTests(unittest.TestCase):
    def test_perplexity_matches_exponential_loss(self):
        result = score_sequences(
            FakeTorch, FakeEvalModel(), [[1, 2, 3, 4, 5, 6]], FakeConfig(), "cpu"
        )
        self.assertAlmostEqual(math.log(2), result["test_loss"])
        self.assertAlmostEqual(2.0, result["test_perplexity"])
        self.assertEqual(5, result["evaluated_tokens"])

    def test_empty_sequences_rejected(self):
        with self.assertRaises(ValueError):
            score_sequences(
                FakeTorch, FakeEvalModel(), [], FakeConfig(), "cpu"
            )


if __name__ == "__main__":
    unittest.main()
