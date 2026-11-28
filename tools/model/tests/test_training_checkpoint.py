import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from train_transformer import best_checkpoint_path


class BestCheckpointPathTests(unittest.TestCase):
    def test_best_checkpoint_uses_separate_suffix(self):
        output = Path("runs/tata-v0.pt")
        self.assertEqual(Path("runs/tata-v0.best.pt"), best_checkpoint_path(output))

    def test_best_checkpoint_keeps_non_pt_suffix(self):
        output = Path("model.checkpoint")
        self.assertEqual(Path("model.best.checkpoint"), best_checkpoint_path(output))


if __name__ == "__main__":
    unittest.main()
