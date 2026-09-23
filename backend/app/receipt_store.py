"""Append-only lifecycle receipt storage.

Receipt rows are immutable after insertion. Transition validation is performed
against the latest observed state for an actor/task/resource tuple.
"""
from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .consent_receipt import ActionReceipt, ActionStatus


_ALLOWED: dict[ActionStatus, frozenset[ActionStatus]] = {
    ActionStatus.REQUESTED: frozenset({ActionStatus.AUTHORIZED, ActionStatus.DENIED}),
    ActionStatus.AUTHORIZED: frozenset({ActionStatus.DISPATCHED, ActionStatus.FAILED, ActionStatus.UNKNOWN}),
    ActionStatus.DISPATCHED: frozenset({ActionStatus.SUCCEEDED, ActionStatus.FAILED, ActionStatus.UNKNOWN}),
    ActionStatus.SUCCEEDED: frozenset(),
    ActionStatus.FAILED: frozenset(),
    ActionStatus.UNKNOWN: frozenset(),
    ActionStatus.DENIED: frozenset(),
    ActionStatus.CANCELLED: frozenset(),
}


class ReceiptStore:
    def __init__(self, path: str | Path) -> None:
        self.path = str(path)
        if self.path != ":memory:":
            Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        self._initialize()

    def _connect(self) -> sqlite3.Connection:
        db = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA busy_timeout=10000")
        return db

    def _initialize(self) -> None:
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS action_receipts (
                receipt_id TEXT PRIMARY KEY,
                actor_id TEXT NOT NULL,
                action_type TEXT NOT NULL,
                resource TEXT NOT NULL,
                status TEXT NOT NULL,
                occurred_at TEXT NOT NULL,
                grant_id TEXT,
                task_id TEXT,
                request_digest TEXT,
                external_reference TEXT,
                error_code TEXT,
                provenance TEXT
            )""")
            db.execute(
                "CREATE INDEX IF NOT EXISTS idx_receipts_actor_task ON action_receipts(actor_id, task_id, occurred_at)"
            )
            db.execute(
                "CREATE INDEX IF NOT EXISTS idx_receipts_resource ON action_receipts(resource, occurred_at)"
            )

    def append(self, receipt: ActionReceipt) -> None:
        with self._connect() as db:
            db.execute("BEGIN IMMEDIATE")
            if receipt.status != ActionStatus.REQUESTED:
                row = db.execute(
                    """SELECT status FROM action_receipts
                       WHERE actor_id=? AND task_id IS ? AND resource=?
                       ORDER BY occurred_at DESC LIMIT 1""",
                    (receipt.actor_id, receipt.task_id, receipt.resource),
                ).fetchone()
                if row is None:
                    db.rollback()
                    raise ValueError("First receipt for an action must be REQUESTED")
                previous = ActionStatus(row["status"])
                if receipt.status not in _ALLOWED[previous]:
                    db.rollback()
                    raise ValueError(f"Illegal receipt transition: {previous.value} -> {receipt.status.value}")
            try:
                db.execute(
                    """INSERT INTO action_receipts
                       (receipt_id, actor_id, action_type, resource, status, occurred_at,
                        grant_id, task_id, request_digest, external_reference, error_code, provenance)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    (
                        receipt.receipt_id, receipt.actor_id, receipt.action_type,
                        receipt.resource, receipt.status.value,
                        receipt.occurred_at.astimezone(timezone.utc).isoformat(),
                        receipt.grant_id, receipt.task_id, receipt.request_digest,
                        receipt.external_reference, receipt.error_code, receipt.provenance,
                    ),
                )
                db.commit()
            except Exception:
                db.rollback()
                raise

    def list_owned(
        self,
        actor_id: str,
        *,
        task_id: str | None = None,
        limit: int = 100,
    ) -> list[dict[str, Any]]:
        if limit < 1 or limit > 500:
            raise ValueError("limit must be between 1 and 500")
        with self._connect() as db:
            if task_id is None:
                rows = db.execute(
                    """SELECT * FROM action_receipts
                       WHERE actor_id=? ORDER BY occurred_at DESC LIMIT ?""",
                    (actor_id, limit),
                ).fetchall()
            else:
                rows = db.execute(
                    """SELECT * FROM action_receipts
                       WHERE actor_id=? AND task_id=?
                       ORDER BY occurred_at DESC LIMIT ?""",
                    (actor_id, task_id, limit),
                ).fetchall()
        result = []
        for row in rows:
            item = dict(row)
            result.append(item)
        return result
