# Tata ONNX Android PoC

Status: isolated experimental module; not integrated into the Tata app.

## Boundaries

- Uses ONNX Runtime Android 1.32.0, pinned to an explicit version.
- Opens only a caller-supplied local model file.
- Contains no network permission, model downloader, cloud inference, or training corpus.
- Is not a dependency of `:app`; the existing app and APK path remain unchanged.
- Does not replace the current llama.cpp/GGUF compatibility path.
- Does not establish production suitability or device performance.

## Current validation scope

The module exposes a small session-open probe and input/output name inspection. CI compiles the module and resolves the pinned Android AAR. This is not yet a real-device inference test.

Next gates:
1. Add an explicitly synthetic tiny ONNX fixture.
2. Run Android instrumentation on an emulator/device and verify session creation and inference.
3. Compare logits against PyTorch and ONNX Runtime Python for the same fixture.
4. Measure APK/AAR size, initialization time, peak memory, and token latency on target hardware.
5. Decide whether the runtime is suitable before any integration into `:app`.

No production model or training data is included in this PoC.
