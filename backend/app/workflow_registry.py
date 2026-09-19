"""Server-owned ComfyUI workflow registry.

Manifest files are trusted deployment configuration, never uploaded by clients.
The registry intentionally supports only explicit parameter-to-node-input mappings.
"""
from __future__ import annotations

import copy
import json
import os
import re
from pathlib import Path
from typing import Any

from fastapi import HTTPException
from pydantic import BaseModel, ConfigDict, Field, ValidationError

_ID = re.compile(r"^[a-z][a-z0-9_-]{0,63}$")


class ParameterSpec(BaseModel):
    model_config = ConfigDict(extra="forbid")
    type: str
    required: bool = True
    default: Any = None
    min_length: int | None = Field(default=None, ge=0, le=4000)
    max_length: int | None = Field(default=None, ge=1, le=4000)
    minimum: float | None = None
    maximum: float | None = None
    choices: list[str] | None = None


class WorkflowSpec(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: str
    version: int = Field(ge=1)
    label: str
    media_type: str
    template_file: str
    max_nodes: int = Field(default=250, ge=1, le=1000)
    parameters: dict[str, ParameterSpec] = Field(default_factory=dict)
    mappings: dict[str, str] = Field(default_factory=dict)


class WorkflowRegistry:
    def __init__(self, root: str | Path | None = None) -> None:
        self.root = Path(root or os.getenv("WORKFLOW_REGISTRY_DIR", "backend/workflows")).resolve()
        self._specs: dict[str, WorkflowSpec] = {}

    def reload(self) -> None:
        specs: dict[str, WorkflowSpec] = {}
        if not self.root.exists():
            self._specs = {}
            return
        for manifest in sorted(self.root.glob("*.manifest.json")):
            try:
                spec = WorkflowSpec.model_validate_json(manifest.read_text(encoding="utf-8"))
            except (OSError, ValidationError, ValueError) as exc:
                raise RuntimeError(f"Invalid workflow manifest: {manifest.name}") from exc
            if not _ID.fullmatch(spec.id) or spec.id in specs:
                raise RuntimeError(f"Invalid or duplicate workflow id: {spec.id}")
            specs[spec.id] = spec
        self._specs = specs

    def public_list(self) -> list[dict[str, Any]]:
        return [{"id": s.id, "version": s.version, "label": s.label,
                 "type": s.media_type, "parameters": {
                     key: {k: v for k, v in p.model_dump().items() if k != "default" or p.default is not None}
                     for key, p in s.parameters.items()}}
                for s in self._specs.values()]

    def build(self, workflow_id: str, values: dict[str, Any]) -> dict[str, Any]:
        spec = self._specs.get(workflow_id)
        if spec is None:
            raise HTTPException(status_code=404, detail="Workflow not found")
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
                if valid and rule.min_length is not None:
                    valid = len(value) >= rule.min_length
                if valid and rule.max_length is not None:
                    valid = len(value) <= rule.max_length
                if valid and rule.choices is not None:
                    valid = value in rule.choices
            elif rule.type == "integer":
                valid = isinstance(value, int) and not isinstance(value, bool)
                if valid and rule.minimum is not None:
                    valid = value >= rule.minimum
                if valid and rule.maximum is not None:
                    valid = value <= rule.maximum
            elif rule.type == "number":
                valid = isinstance(value, (int, float)) and not isinstance(value, bool)
                if valid and rule.minimum is not None:
                    valid = value >= rule.minimum
                if valid and rule.maximum is not None:
                    valid = value <= rule.maximum
            elif rule.type == "boolean":
                valid = isinstance(value, bool)
            else:
                raise HTTPException(status_code=500, detail="Unsupported parameter type in registry")
            if not valid:
                raise HTTPException(status_code=422, detail=f"Invalid parameter: {name}")
            resolved[name] = value

        # Resolve template only beneath registry root; reject symlink/path traversal.
        candidate = (self.root / spec.template_file).resolve()
        if candidate.parent != self.root or candidate.is_symlink() or not candidate.is_file():
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
                node = result[node_id]
                inputs = node["inputs"]
                if not isinstance(inputs, dict) or input_name not in inputs:
                    raise KeyError
                inputs[input_name] = resolved[parameter]
            except (ValueError, KeyError, TypeError) as exc:
                raise HTTPException(status_code=503, detail="Workflow mapping does not match template") from exc
        if set(spec.mappings) - set(spec.parameters):
            raise HTTPException(status_code=503, detail="Workflow mapping references an unknown parameter")
        return result


registry = WorkflowRegistry()
try:
    registry.reload()
except RuntimeError:
    # Fail closed for v2 discovery/submission; do not load malformed configuration.
    registry = WorkflowRegistry(root="/__invalid_workflow_registry__")
