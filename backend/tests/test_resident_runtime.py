from pathlib import Path

import pytest

import app.omni_runtime as runtime
import app.model_vault as vault


def test_resident_runtime_requires_exactly_three_slots():
    with pytest.raises(Exception):
        runtime.LoadAllRequest(slots=[
            runtime.ResidentModel(model_path="/models/a.gguf", model_name="a.gguf"),
            runtime.ResidentModel(model_path="/models/b.gguf", model_name="b.gguf"),
        ])


def test_resident_runtime_rejects_non_gguf():
    with pytest.raises(Exception):
        runtime._validate_model_path(
            runtime.ResidentModel(model_path="/models/image.safetensors", model_name="image.safetensors")
        )


def test_hf_download_requires_gguf_filename():
    with pytest.raises(Exception):
        vault._validate_gguf_filename("model.safetensors")


def test_hf_download_accepts_nested_filename_without_path_traversal():
    assert vault._validate_gguf_filename("model.Q4_K_M.gguf") == "model.Q4_K_M.gguf"
