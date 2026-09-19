"""Server-owned ComfyUI workflow registry.

Manifest files are trusted deployment configuration, never uploaded by clients.
"""
from __future__ import annotations

import copy
import json
import os
import re
from pathlib import Path
from typing import Any, Literal

from fastapi import HTTPException
from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

_ID = re.compile(r"^[a-z][a-z0-9_-]{0,63}$")


class ParameterSpec(BaseModel):
    model_config = ConfigDict(extra="forbid")
    type: Literal["string", "integer", "number", "boolean"]
    required: bool = True
    default: Any = None
    min_length: int | None = Field(default=None, ge=0, le=4000)
    max_length: int | None = Field(default=None, ge=1, le=4000)
    minimum: float | None = None
    maximum: float | None = None
    choices: list[str] | None = None

    @model_validator(mode="after")
    def validate_constraints(self):
        if self.min_length is not None and self.max_length is not None and self.min_length > self.max_length:
            raise ValueError("min_length cannot exceed max_length")
        if self.minimum is not None and self.maximum is not None and self.minimum > self.maximum:
            raise ValueError("minimum cannot exceed maximum")
        if self.choices is not None and self.type != "string":
            raise ValueError("choices are supported only for string parameters")
        return self


class WorkflowSpec(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str
    version: int = Field(ge=1)
    label: str
    media_type: Literal["IMAGE", "VIDEO"]
    template_file: str
    max_nodes: int = Field(default=250, ge=1, le=1000)
    parameters: dict[str, ParameterSpec] = Field(default_factory=dict)
    mappings: dict[str, str] = Field(default_factory=dict)


class WorkflowRegistry:
    def __init__(self, root: str | Path | None = None) -> None:
        configured = root if root is not None else os.getenv("WORKFLOW_REGISTRY_DIR")
        raw_root = Path(configured) if configured is not None else Path(__file__).resolve().parents[1] / "workflows"
        self.root = raw_root.resolve()
        self._specs: dict[str, WorkflowSpec] = {}

    def reload(self) -> None:
        specs: dict[str, WorkflowSpec] = {}
        if not self.root.exists():
            self._specs = {}
            return
        for manifest in sorted(self.root.glob("*.manifest.json")):
            if manifest.is_symlink():
                raise RuntimeError(f"Workflow manifest must not be a symlink: {manifest.name}")
            try:
                spec = WorkflowSpec.model_validate_json(manifest.read_text(encoding="utf-8"))
            except (OSError, ValidationError, ValueError) as exc:
                raise RuntimeError(f"Invalid workflow manifest: {manifest.name}") from exc
            if not _ID.fullmatch(spec.id) or spec.id in specs:
                raise RuntimeError(f"Invalid or duplicate workflow id: {spec.id}")
            if Path(spec.template_file).name != spec.template_file or spec.template_file in (".", ".."):
                raise RuntimeError(f"Invalid workflow template filename: {spec.template_file}")
            if set(spec.mappings) - set(spec.parameters):
                raise RuntimeError(f"Workflow mapping references unknown parameter: {spec.id}")
            specs[spec.id] = spec
        self._specs = specs

    def get_spec(self, workflow_id: str) -> WorkflowSpec:
        spec = self._specs.get(workflow_id)
        if spec is None:
            raise HTTPException(status_code=404, detail="Workflow not found")
        return spec

    def public_list(self) -> list[dict[str, Any]]:
        return [{"id": s.id, "version": s.version, "label": s.label,
                 "type": s.media_type, "parameters": {
                     key: {k: v for k, v in p.model_dump().items() if k != "default" or p.default is not None}
                     for key, p in s.parameters.items()}}
                for s in self._specs.values()]

    def build(self, workflow_id: str, values: dict[str, Any]) -> dict[str, Any]:
        spec = self.get_spec(workflow_id)
        unknown = set(values) - set(spec.parameters)
        if unknown:
            raise HTTPException(status_code=422, detail="Unknown workflow parameter")
        resolved: dict[str, Any] = {}
        for name, rule in spec.parameters.items():
            value = values.get(name, rule.default)
            if value is None:
                if rule.required:
                    raise HTTPException(status_code=422, detail=f"Missing parameter: {name}")
                continue
            if rule.type == "string":
                valid = isinstance(value, str)
                if valid and rule.min_length is not None: valid = len(value) >= rule.min_length
                if valid and rule.max_length is not None: valid = len(value) <= rule.max_length
                if valid and rule.choices is not None: valid = value in rule.choices
            elif rule.type == "integer":
                valid = isinstance(value, int) and not isinstance(value, bool)
                if valid and rule.minimum is not None: valid = value >= rule.minimum
                if valid and rule.maximum is not None: valid = value <= rule.maximum
            elif rule.type == "number":
                valid = isinstance(value, (int, float)) and not isinstance(value, bool)
                if valid and rule.minimum is not None: valid = value >= rule.minimum
                if valid and rule.maximum is not None: valid = value <= rule.maximum
            else:
                valid = isinstance(value, bool)
            if not valid:
                raise HTTPException(status_code=422, detail=f"Invalid parameter: {name}")
            resolved[name] = value

        raw_candidate = self.root / spec.template_file
        if raw_candidate.is_symlink():
            raise HTTPException(status_code=503, detail="Workflow template is unavailable")
        candidate = raw_candidate.resolve()
        if candidate.parent != self.root or not candidate.is_file():
            raise HTTPException(status_code=503, detail="Workflow template is unavailable")
        try:
            graph = json.loads(candidate.read_text(encoding="utf-8"))
        except (OSError, ValueError) as exc:
            raise HTTPException(status_code=503, detail="Workflow template is invalid") from exc
        if not isinstance(graph, dict) or not graph or len(graph) > spec.max_nodes:
            raise HTTPException(status_code=503, detail="Workflow template violates registry limits")
        result = copy.deepcopy(graph)
        for parameter, target in spec.mappings.items():
            if parameter not in resolved:
                continue
            try:
                node_id, input_name = target.split(".", 1)
                if not node_id or not input_name: raise ValueError
                node = result[node_id]
                inputs = node["inputs"]
                if not isinstance(inputs, dict) or input_name not in inputs: raise KeyError
                inputs[input_name] = resolved[parameter]
            except (ValueError, KeyError, TypeError) as exc:
                raise HTTPException(status_code=503, detail="Workflow mapping does not match template") from exc
        return result


registry = WorkflowRegistry()
try:
    registry.reload()
except RuntimeError:
    registry = WorkflowRegistry(root="/__invalid_workflow_registry__")
