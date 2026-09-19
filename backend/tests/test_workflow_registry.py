import json

import pytest
from fastapi import HTTPException

from app.workflow_registry import WorkflowRegistry


def make_registry(tmp_path, *, manifest_overrides=None, graph=None):
    graph = graph or {"1": {"class_type": "CLIPTextEncode", "inputs": {"text": "placeholder"}}}
    (tmp_path / "template.json").write_text(json.dumps(graph), encoding="utf-8")
    manifest = {
        "id": "test-image",
        "version": 1,
        "label": "Test image",
        "media_type": "IMAGE",
        "template_file": "template.json",
        "parameters": {
            "prompt": {"type": "string", "required": True, "min_length": 1, "max_length": 100}
        },
        "mappings": {"prompt": "1.text"},
    }
    manifest.update(manifest_overrides or {})
    (tmp_path / "test.manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    registry = WorkflowRegistry(tmp_path)
    registry.reload()
    return registry


def test_build_applies_parameter_to_server_template(tmp_path):
    registry = make_registry(tmp_path)
    result = registry.build("test-image", {"prompt": "a mountain"})
    assert result["1"]["inputs"]["text"] == "a mountain"


def test_build_does_not_mutate_template_between_calls(tmp_path):
    registry = make_registry(tmp_path)
    registry.build("test-image", {"prompt": "first"})
    result = registry.build("test-image", {"prompt": "second"})
    assert result["1"]["inputs"]["text"] == "second"


def test_unknown_parameter_is_rejected(tmp_path):
    registry = make_registry(tmp_path)
    with pytest.raises(HTTPException) as exc:
        registry.build("test-image", {"prompt": "ok", "workflow": {}})
    assert exc.value.status_code == 422


def test_invalid_parameter_value_is_rejected(tmp_path):
    registry = make_registry(tmp_path)
    with pytest.raises(HTTPException) as exc:
        registry.build("test-image", {"prompt": ""})
    assert exc.value.status_code == 422


def test_unknown_workflow_is_404(tmp_path):
    registry = make_registry(tmp_path)
    with pytest.raises(HTTPException) as exc:
        registry.get_spec("missing")
    assert exc.value.status_code == 404


def test_path_traversal_template_is_rejected_at_load(tmp_path):
    with pytest.raises(RuntimeError):
        make_registry(tmp_path, manifest_overrides={"template_file": "../outside.json"})


def test_invalid_media_type_is_rejected_at_load(tmp_path):
    with pytest.raises(RuntimeError):
        make_registry(tmp_path, manifest_overrides={"media_type": "AUDIO"})


def test_mapping_to_missing_input_fails_closed(tmp_path):
    registry = make_registry(tmp_path, manifest_overrides={"mappings": {"prompt": "1.missing"}})
    with pytest.raises(HTTPException) as exc:
        registry.build("test-image", {"prompt": "ok"})
    assert exc.value.status_code == 503


def test_template_symlink_is_rejected(tmp_path):
    registry = make_registry(tmp_path)
    (tmp_path / "template.json").unlink()
    outside = tmp_path.parent / "outside-template.json"
    outside.write_text(json.dumps({"1": {"inputs": {"text": "external"}}}), encoding="utf-8")
    try:
        (tmp_path / "template.json").symlink_to(outside)
    except (OSError, NotImplementedError):
        pytest.skip("Symlinks are not supported in this environment")
    with pytest.raises(HTTPException) as exc:
        registry.build("test-image", {"prompt": "ok"})
    assert exc.value.status_code == 503
