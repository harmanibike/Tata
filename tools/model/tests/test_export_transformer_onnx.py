from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

try:
    import torch
    import onnx
except ImportError:
    torch = None
    onnx = None

from tools.corpus.train_tokenizer import CharacterBPE
from tools.model.transformer_config import TransformerConfig
from tools.model.train_transformer import build_model_class
from tools.model.export_transformer_onnx import export, load_best_checkpoint


@unittest.skipIf(torch is None or onnx is None, "PyTorch and ONNX are installed by the runtime workflow")
class TataOnnxExportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.config = TransformerConfig(
            vocab_size=6, hidden_size=8, num_layers=1, num_heads=2,
            intermediate_size=16, context_length=8, tie_embeddings=True,
        )
        tokenizer = CharacterBPE(["a", "b"], [])
        self.tokenizer_path = self.root / "tokenizer.json"
        tokenizer.save(self.tokenizer_path)
        model = build_model_class(torch)(self.config)
        self.checkpoint_path = self.root / "best.pt"
        torch.save({
            "format": "tata-transformer-checkpoint",
            "format_version": 1,
            "checkpoint_role": "best_validation",
            "step": 3,
            "seed": 17,
            "config": self.config.report(),
            "model": model.state_dict(),
            "train_meta": {"corpus_sha256": "synthetic-train", "source_register_sha256": "synthetic-register"},
            "validation_meta": {"corpus_sha256": "synthetic-validation"},
            "training": {"initialization": "random", "pretrained_weights": False},
        }, self.checkpoint_path)

    def tearDown(self):
        self.temp.cleanup()

    def test_export_writes_checked_onnx_and_bound_manifest(self):
        output = self.root / "tata-experimental.onnx"
        result = export(self.checkpoint_path, self.tokenizer_path, output)
        self.assertTrue(output.is_file())
        graph = onnx.load(str(output))
        onnx.checker.check_model(graph)
        manifest_path = Path(result["manifest"])
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual(manifest["status"], "experimental_not_android_validated")
        self.assertEqual(manifest["tokenizer"]["vocab_size"], 6)
        self.assertEqual(manifest["tokenizer"]["id_to_token"][:4], ["<pad>", "<unk>", "<bos>", "<eos>"])
        self.assertEqual(manifest["provenance"]["initialization"], "random")
        self.assertFalse(manifest["provenance"]["pretrained_weights"])
        self.assertEqual(manifest["model"]["sha256"], result["sha256"])

    def test_export_rejects_resume_checkpoint(self):
        payload = torch.load(self.checkpoint_path, map_location="cpu", weights_only=True)
        payload["checkpoint_role"] = "latest_resume"
        torch.save(payload, self.checkpoint_path)
        with self.assertRaisesRegex(ValueError, "best_validation"):
            load_best_checkpoint(torch, self.checkpoint_path)


if __name__ == "__main__":
    unittest.main()
