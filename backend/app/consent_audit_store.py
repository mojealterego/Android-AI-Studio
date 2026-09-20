"""Append-only SQLite journal for consequential action receipts.

This module stores receipt snapshots as immutable events. It deliberately does
not authenticate actors or authorize actions; callers must derive actor_id from
an authenticated principal and append only server-observed lifecycle states.
Never put credentials, access tokens, or raw secret material in a receipt.
"""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path
from typing import Any

from .consent_receipt import ActionReceipt


class ConsentAuditStore:
    """Persist receipt events without update/delete operations."""

    def __init__(self, path: str | Path) -> None:
        self.path = str(path)
        if self.path != ":memory:":
            Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS action_receipt_events (
                event_id INTEGER PRIMARY KEY AUTOINCREMENT,
                receipt_id TEXT NOT NULL,
                actor_id TEXT NOT NULL,
                task_id TEXT,
                status TEXT NOT NULL,
                occurred_at TEXT NOT NULL,
                receipt_json TEXT NOT NULL
            )""")
            db.execute("CREATE INDEX IF NOT EXISTS idx_receipt_events_id ON action_receipt_events(receipt_id, event_id)")
            db.execute("CREATE INDEX IF NOT EXISTS idx_receipt_events_actor ON action_receipt_events(actor_id, event_id)")

    def _connect(self) -> sqlite3.Connection:
        db = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA busy_timeout=10000")
        return db

    def append(self, receipt: ActionReceipt) -> int:
        """Append one immutable receipt snapshot; return its event sequence."""
        payload = json.dumps(receipt.to_dict(), ensure_ascii=False, separators=(",", ":"))
        with self._connect() as db:
            cur = db.execute(
                "INSERT INTO action_receipt_events "
                "(receipt_id, actor_id, task_id, status, occurred_at, receipt_json) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                (receipt.receipt_id, receipt.actor_id, receipt.task_id,
                 receipt.status.value, receipt.to_dict()["occurred_at"], payload),
            )
            return int(cur.lastrowid)

    def get_events(self, receipt_id: str) -> list[dict[str, Any]]:
        """Return a receipt's immutable events in append order."""
        with self._connect() as db:
            rows = db.execute(
                "SELECT event_id, receipt_json FROM action_receipt_events "
                "WHERE receipt_id=? ORDER BY event_id ASC", (receipt_id,)
            ).fetchall()
        return [{"event_id": row["event_id"], **json.loads(row["receipt_json"])} for row in rows]

    def list_events(self, *, actor_id: str, limit: int = 100) -> list[dict[str, Any]]:
        """List newest events for one actor; bounded to 1..500 rows."""
        if not isinstance(actor_id, str) or not actor_id.strip():
            raise ValueError("actor_id is required")
        if isinstance(limit, bool) or not isinstance(limit, int) or not 1 <= limit <= 500:
            raise ValueError("limit must be an integer between 1 and 500")
        with self._connect() as db:
            rows = db.execute(
                "SELECT event_id, receipt_json FROM action_receipt_events "
                "WHERE actor_id=? ORDER BY event_id DESC LIMIT ?", (actor_id, limit)
            ).fetchall()
        return [{"event_id": row["event_id"], **json.loads(row["receipt_json"])} for row in rows]
