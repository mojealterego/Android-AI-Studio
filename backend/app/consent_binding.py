"""Canonical, parameter-bound consent resource identifiers.

A grant created for one validated workflow payload cannot authorize a different
payload. This module deliberately does not authenticate principals or approve
requests; callers must do those steps explicitly.
"""
from __future__ import annotations

import hashlib
import json
from typing import Any


def action_digest(workflow_id: str, parameters: dict[str, Any]) -> str:
    """Return a stable SHA-256 digest for a workflow ID and JSON parameters."""
    if not workflow_id or not isinstance(parameters, dict):
        raise ValueError("workflow_id and parameters are required")
    try:
        canonical = json.dumps(
            {"workflow_id": workflow_id, "parameters": parameters},
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
            allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError) as exc:
        raise ValueError("parameters must be finite JSON-compatible data") from exc
    return hashlib.sha256(canonical).hexdigest()


def consent_resource(workflow_id: str, parameters: dict[str, Any]) -> str:
    """Build the exact resource key that an approval must authorize."""
    return f"workflow:{workflow_id}:sha256:{action_digest(workflow_id, parameters)}"


def workflow_resource(workflow_id: str, parameters: dict[str, Any]) -> str:
    """Backward-compatible name used by consent_dispatch."""
    return consent_resource(workflow_id, parameters)
