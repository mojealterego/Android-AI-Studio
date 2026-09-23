"""Durable SQLite job store for generation lifecycle state.

The store is the source of truth for job ownership and lifecycle metadata.
SQLite is suitable for a single backend instance or a shared persistent volume;
a multi-replica deployment should use a shared transactional database.
"""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path
from typing import Any


class JobStore:
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
            db.execute("""CREATE TABLE IF NOT EXISTS jobs (
                id TEXT PRIMARY KEY,
                prompt_id TEXT NOT NULL,
                client_id TEXT NOT NULL,
                owner_id TEXT NOT NULL,
                type TEXT NOT NULL,
                status TEXT NOT NULL,
                progress REAL NOT NULL DEFAULT 0,
                workflow_id TEXT,
                workflow_version INTEGER,
                parameters_json TEXT,
                outputs_json TEXT,
                error_code TEXT,
                error_detail TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )""")
            db.execute("CREATE INDEX IF NOT EXISTS idx_jobs_owner_updated ON jobs(owner_id, updated_at DESC)")
            db.execute("CREATE INDEX IF NOT EXISTS idx_jobs_prompt ON jobs(prompt_id)")

    @staticmethod
    def _decode(row: sqlite3.Row) -> dict[str, Any]:
        job = dict(row)
        for key in ("parameters_json", "outputs_json"):
            raw = job.pop(key, None)
            target = key.removesuffix("_json")
            job[target] = json.loads(raw) if raw else ({} if target == "parameters" else [])
        return job

    def create(self, job: dict[str, Any]) -> dict[str, Any]:
        with self._connect() as db:
            db.execute("""INSERT INTO jobs
                (id, prompt_id, client_id, owner_id, type, status, progress,
                 workflow_id, workflow_version, parameters_json, outputs_json,
                 error_code, error_detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (job["id"], job["prompt_id"], job["client_id"], job["owner_id"],
                 job["type"], job["status"], float(job.get("progress", 0.0)),
                 job.get("workflow_id"), job.get("workflow_version"),
                 json.dumps(job.get("parameters", {}), sort_keys=True),
                 json.dumps(job.get("outputs", []), sort_keys=True),
                 job.get("error_code"), job.get("error_detail")))
        return dict(job)

    def get(self, job_id: str) -> dict[str, Any] | None:
        with self._connect() as db:
            row = db.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
        return self._decode(row) if row else None

    def list_owned(self, owner_id: str, limit: int = 500) -> list[dict[str, Any]]:
        with self._connect() as db:
            rows = db.execute(
                "SELECT * FROM jobs WHERE owner_id=? ORDER BY created_at DESC LIMIT ?",
                (owner_id, limit),
            ).fetchall()
        return [self._decode(row) for row in rows]

    def count(self) -> int:
        with self._connect() as db:
            return int(db.execute("SELECT COUNT(*) FROM jobs").fetchone()[0])

    def update(self, job_id: str, **changes: Any) -> dict[str, Any]:
        allowed = {
            "status", "progress", "outputs", "error_code", "error_detail",
        }
        unknown = set(changes) - allowed
        if unknown:
            raise ValueError(f"Unsupported job fields: {sorted(unknown)}")
        if not changes:
            current = self.get(job_id)
            if current is None:
                raise KeyError(job_id)
            return current
        assignments = []
        values: list[Any] = []
        for key, value in changes.items():
            column = key + "_json" if key == "outputs" else key
            assignments.append(f"{column}=?")
            values.append(json.dumps(value, sort_keys=True) if key == "outputs" else value)
        assignments.append("updated_at=CURRENT_TIMESTAMP")
        values.append(job_id)
        with self._connect() as db:
            cur = db.execute(
                f"UPDATE jobs SET {', '.join(assignments)} WHERE id=?", values
            )
            if cur.rowcount != 1:
                raise KeyError(job_id)
        return self.get(job_id)  # type: ignore[return-value]

    def clear(self) -> None:
        with self._connect() as db:
            db.execute("DELETE FROM jobs")
