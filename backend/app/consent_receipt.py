"""Structured, privacy-conscious records for consequential agent actions.

This module defines a serialization contract only. Persistence, authenticated
actor attribution, and tamper-evident storage must be supplied by the service.
Never place credentials or raw secret material in receipt fields.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from enum import Enum
from typing import Any


class ActionStatus(str, Enum):
    REQUESTED = "requested"
    AUTHORIZED = "authorized"
    DISPATCHED = "dispatched"
    SUCCEEDED = "succeeded"
    FAILED = "failed"
    UNKNOWN = "unknown"
    DENIED = "denied"
    CANCELLED = "cancelled"


@dataclass(frozen=True)
class ActionReceipt:
    receipt_id: str
    actor_id: str
    action_type: str
    resource: str
    status: ActionStatus
    occurred_at: datetime
    grant_id: str | None = None
    task_id: str | None = None
    request_digest: str | None = None
    external_reference: str | None = None
    error_code: str | None = None
    provenance: str | None = None

    def __post_init__(self) -> None:
        for field_name in ("receipt_id", "actor_id", "action_type", "resource"):
            value = getattr(self, field_name)
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"{field_name} is required")
        if self.occurred_at.tzinfo is None:
            raise ValueError("occurred_at must be timezone-aware")
        for field_name in ("receipt_id", "actor_id", "action_type", "resource", "grant_id", "task_id", "request_digest", "external_reference", "error_code", "provenance"):
            value = getattr(self, field_name)
            if value is not None and len(value) > 2048:
                raise ValueError(f"{field_name} exceeds 2048 characters")

    def to_dict(self) -> dict[str, Any]:
        """Return JSON-ready data with an explicit UTC timestamp and status."""
        result = asdict(self)
        result["status"] = self.status.value
        result["occurred_at"] = self.occurred_at.astimezone(timezone.utc).isoformat()
        return result
