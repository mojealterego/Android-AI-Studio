from pathlib import Path

from app.model_vault import _safe_model_path


def test_model_path_is_contained(monkeypatch, tmp_path):
    monkeypatch.setattr("app.model_vault.MODEL_ROOT", Path(tmp_path).resolve())
    assert _safe_model_path("chat/model.gguf").parent == Path(tmp_path).resolve() / "chat"


def test_model_path_rejects_traversal(monkeypatch, tmp_path):
    monkeypatch.setattr("app.model_vault.MODEL_ROOT", Path(tmp_path).resolve())
    try:
        _safe_model_path("../escape.gguf")
    except Exception:
        pass
    else:
        raise AssertionError("path traversal was accepted")


def test_model_filename_requires_gguf():
    from app.model_vault import _validate_gguf_filename
    assert _validate_gguf_filename("Qwen3-8B-Q4_K_M.gguf") == "Qwen3-8B-Q4_K_M.gguf"


def test_model_filename_rejects_non_gguf():
    from app.model_vault import _validate_gguf_filename
    try:
        _validate_gguf_filename("model.safetensors")
    except Exception:
        pass
    else:
        raise AssertionError("non-GGUF model was accepted")
