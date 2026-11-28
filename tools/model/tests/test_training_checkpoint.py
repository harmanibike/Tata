import random
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from train_transformer import best_checkpoint_path, early_stop_reached, load_resume_checkpoint, atomic_save
from unittest.mock import Mock


class BestCheckpointPathTests(unittest.TestCase):
    def test_best_checkpoint_uses_separate_suffix(self):
        output = Path("runs/tata-v0.pt")
        self.assertEqual(Path("runs/tata-v0.best.pt"), best_checkpoint_path(output))

    def test_best_checkpoint_keeps_non_pt_suffix(self):
        output = Path("model.checkpoint")
        self.assertEqual(Path("model.best.checkpoint"), best_checkpoint_path(output))

    def test_early_stopping_disabled_with_zero_patience(self):
        self.assertFalse(early_stop_reached(0, 100))

    def test_early_stopping_waits_until_patience(self):
        self.assertFalse(early_stop_reached(3, 2))
        self.assertTrue(early_stop_reached(3, 3))
        self.assertTrue(early_stop_reached(3, 4))


class SafeCheckpointLoadingTests(unittest.TestCase):
    def valid_checkpoint(self):
        return {
            "format": "tata-transformer-checkpoint",
            "format_version": 1,
            "checkpoint_role": "latest_resume",
            "step": 12,
            "config": {},
            "model": {},
            "optimizer": {},
            "train_meta": {},
            "validation_meta": {},
        }

    def test_load_uses_weights_only_and_validates_checkpoint(self):
        torch = Mock()
        torch.load.return_value = self.valid_checkpoint()
        result = load_resume_checkpoint(torch, Path("resume.pt"), "cpu")
        torch.load.assert_called_once_with(
            Path("resume.pt"), map_location="cpu", weights_only=True
        )
        self.assertEqual(result["step"], 12)

    def test_rejects_wrong_checkpoint_format(self):
        torch = Mock()
        torch.load.return_value = {"format": "other"}
        with self.assertRaisesRegex(ValueError, "bukan milik Tata"):
            load_resume_checkpoint(torch, Path("bad.pt"), "cpu")

    def test_rejects_incomplete_checkpoint(self):
        torch = Mock()
        checkpoint = self.valid_checkpoint()
        del checkpoint["optimizer"]
        torch.load.return_value = checkpoint
        with self.assertRaisesRegex(ValueError, "tidak lengkap"):
            load_resume_checkpoint(torch, Path("bad.pt"), "cpu")


    def test_real_checkpoint_round_trip_with_rng_state_is_weights_only_safe(self):
        try:
            import torch
        except ImportError:
            self.skipTest("PyTorch tidak tersedia pada environment ini")
        payload = self.valid_checkpoint()
        payload["model"] = {"weight": torch.tensor([1.0, 2.0])}
        payload["optimizer"] = {"state": {}, "param_groups": []}
        payload["python_rng_state"] = random.Random(7).getstate()
        payload["torch_rng_state"] = torch.get_rng_state()
        payload["cuda_rng_state"] = None
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "resume.pt"
            atomic_save(torch, payload, path)
            loaded = load_resume_checkpoint(torch, path, "cpu")
        self.assertEqual(loaded["step"], 12)
        self.assertTrue(torch.equal(loaded["model"]["weight"], payload["model"]["weight"]))
        self.assertEqual(loaded["python_rng_state"], payload["python_rng_state"])

    def test_does_not_fall_back_when_weights_only_is_unsupported(self):
        torch = Mock()
        torch.load.side_effect = TypeError("unsupported keyword")
        with self.assertRaisesRegex(RuntimeError, "pemuatan checkpoint aman"):
            load_resume_checkpoint(torch, Path("legacy.pt"), "cpu")
        torch.load.assert_called_once()



if __name__ == "__main__":
    unittest.main()
