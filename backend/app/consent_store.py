"""SQLite-backed consent grant storage with atomic one-time consumption.

This module is intentionally separate from FastAPI wiring. Use a persistent
local volume for the database; for multi-worker deployments use a shared
transactional database instead of per-container SQLite files.
"""
from __future__ import annotations

import json
import sqlite3
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

from .consent_core import Access, Duration, Grant, authorize


def _iso(value: datetime | None) -> str | None:
    return value.astimezone(timezone.utc).isoformat() if value is not None else None


def _dt(value: str | None) -> datetime | None:
    return datetime.fromisoformat(value) if value else None


class ConsentStore:
    """Persist grants and enforce one-time use in a single DB transaction."""

    def __init__(self, path: str | Path) -> None:
        self.path = str(path)
        if self.path != ":memory:":
            Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        self._initialize()

    def _connect(self) -> sqlite3.Connection:
        db = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA busy_timeout=10000")
        return db

    def _initialize(self) -> None:
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS consent_grants (
                grant_id TEXT PRIMARY KEY,
                subject_id TEXT NOT NULL,
                resource TEXT NOT NULL,
                access_json TEXT NOT NULL,
                duration TEXT NOT NULL,
                created_at TEXT NOT NULL,
                expires_at TEXT,
                revoked_at TEXT,
                session_id TEXT,
                task_id TEXT,
                consumed_at TEXT
            )""")
            db.execute("CREATE INDEX IF NOT EXISTS idx_consent_subject_resource ON consent_grants(subject_id, resource)")

    def create(self, grant_id: str, grant: Grant) -> None:
        if not grant_id.strip():
            raise ValueError("grant_id is required")
        with self._connect() as db:
            db.execute("""INSERT INTO consent_grants
                (grant_id, subject_id, resource, access_json, duration, created_at,
                 expires_at, revoked_at, session_id, task_id, consumed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)""",
                (grant_id, grant.subject_id, grant.resource,
                 json.dumps(sorted(x.value for x in grant.access)), grant.duration.value,
                 _iso(grant.created_at), _iso(grant.expires_at), _iso(grant.revoked_at),
                 grant.session_id, grant.task_id))

    def get(self, grant_id: str) -> Grant | None:
        with self._connect() as db:
            row = db.execute("SELECT * FROM consent_grants WHERE grant_id=?", (grant_id,)).fetchone()
        return self._to_grant(row) if row else None

    @staticmethod
    def _to_grant(row: sqlite3.Row) -> Grant:
        return Grant(
            subject_id=row["subject_id"], resource=row["resource"],
            access=frozenset(Access(v) for v in json.loads(row["access_json"])),
            duration=Duration(row["duration"]), created_at=_dt(row["created_at"]),
            expires_at=_dt(row["expires_at"]), revoked_at=_dt(row["revoked_at"]),
            session_id=row["session_id"], task_id=row["task_id"],
        )

    def revoke(self, grant_id: str, *, now: datetime | None = None) -> bool:
        instant = now or datetime.now(timezone.utc)
        if instant.tzinfo is None:
            raise ValueError("now must be timezone-aware")
        with self._connect() as db:
            cur = db.execute("UPDATE consent_grants SET revoked_at=? WHERE grant_id=? AND revoked_at IS NULL",
                             (_iso(instant), grant_id))
            return cur.rowcount == 1

    def authorize_and_consume_once(
        self, grant_id: str, *, subject_id: str, resource: str, access: Access,
        session_id: str | None = None, task_id: str | None = None,
        now: datetime | None = None,
    ) -> Grant:
        """Check grant and atomically consume it if duration is ONCE.

        Caller must invoke this immediately before the protected side effect.
        """
        instant = now or datetime.now(timezone.utc)
        if instant.tzinfo is None:
            raise ValueError("now must be timezone-aware")
        db = self._connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT * FROM consent_grants WHERE grant_id=?", (grant_id,)).fetchone()
            if row is None:
                from .consent_core import ConsentError
                raise ConsentError("No consent grant")
            grant = self._to_grant(row)
            consumed = row["consumed_at"] is not None
            authorize(grant, subject_id=subject_id, resource=resource, access=access,
                      session_id=session_id, task_id=task_id, now=instant,
                      consumed_once=consumed)
            if grant.duration == Duration.ONCE:
                cur = db.execute("UPDATE consent_grants SET consumed_at=? WHERE grant_id=? AND consumed_at IS NULL AND revoked_at IS NULL",
                                 (_iso(instant), grant_id))
                if cur.rowcount != 1:
                    from .consent_core import ConsentError
                    raise ConsentError("Once grant has already been consumed")
            db.commit()
            return grant
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()
