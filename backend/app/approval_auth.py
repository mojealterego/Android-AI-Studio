"""Trusted approval-token authentication for the local human approval boundary.

Approval credentials are intentionally separate from the normal API service
credential. The configured mapping is deployment secret material; callers never
supply an actor/subject identifier. The verifier derives the actor from the
credential itself.
"""
from __future__ import annotations

import hmac
import json
import os
from typing import Mapping


class ApprovalAuthenticationError(PermissionError):
    """Raised when no configured approval credential matches."""


def _configured_tokens() -> Mapping[str, str]:
    raw = os.getenv("APPROVAL_TOKENS_JSON", "").strip()
    if not raw:
        return {}
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise RuntimeError("APPROVAL_TOKENS_JSON is invalid") from exc
    if not isinstance(value, dict):
        raise RuntimeError("APPROVAL_TOKENS_JSON must be an object")
    result: dict[str, str] = {}
    for subject, token in value.items():
        if not isinstance(subject, str) or not subject.strip():
            raise RuntimeError("Approval subject identifiers must be non-empty strings")
        if not isinstance(token, str) or len(token) < 16:
            raise RuntimeError("Approval tokens must be at least 16 characters")
        result[subject] = token
    return result


def authenticate_approver(token: str | None) -> str:
    """Return the server-configured approver subject or fail closed."""
    if not token:
        raise ApprovalAuthenticationError("Approval credential required")
    for subject, expected in _configured_tokens().items():
        if hmac.compare_digest(token, expected):
            return subject
    raise ApprovalAuthenticationError("Invalid approval credential")
