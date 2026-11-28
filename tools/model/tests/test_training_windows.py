import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from train_transformer import select_validation_windows


class ValidationWindowTests(unittest.TestCase):
    def test_selects_all_when_under_limit(self):
        sequences = [list(range(6)), list(range(10, 16))]
        windows = select_validation_windows(sequences, context_length=4, max_batches=32)
        self.assertEqual(2, len(windows))
        self.assertEqual([0, 1, 2, 3, 4], windows[0])
        self.assertEqual([10, 11, 12, 13, 14], windows[1])

    def test_samples_evenly_across_all_sequences(self):
        sequences = [list(range(start, start + 41)) for start in (0, 100, 200, 300)]
        windows = select_validation_windows(sequences, context_length=4, max_batches=4)
        self.assertEqual(4, len(windows))
        self.assertEqual(0, windows[0][0])
        self.assertGreater(windows[-1][0], 300)
        self.assertGreater(windows[1][0], windows[0][0])
        self.assertGreater(windows[2][0], windows[1][0])

    def test_one_batch_uses_first_window(self):
        windows = select_validation_windows([list(range(20))], 4, 1)
        self.assertEqual([[0, 1, 2, 3, 4]], windows)

    def test_short_sequences_are_ignored(self):
        self.assertEqual([], select_validation_windows([[1, 2], [3, 4, 5]], 4, 8))

    def test_invalid_limits_rejected(self):
        with self.assertRaises(ValueError):
            select_validation_windows([[1, 2, 3]], 0, 8)
        with self.assertRaises(ValueError):
            select_validation_windows([[1, 2, 3]], 2, 0)


if __name__ == "__main__":
    unittest.main()
