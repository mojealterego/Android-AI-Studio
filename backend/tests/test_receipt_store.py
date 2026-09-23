from datetime import datetime, timezone

import pytest

from app.consent_receipt import ActionReceipt, ActionStatus
from app.receipt_store import ReceiptStore


def receipt(status, receipt_id):
    return ActionReceipt(
        receipt_id=receipt_id,
        actor_id="human-1",
        action_type="workflow.dispatch",
        resource="workflow:image:sha256:abc",
        status=status,
        occurred_at=datetime.now(timezone.utc),
        grant_id="grant-1",
        task_id="task-1",
    )


def test_receipts_are_append_only_and_transitions_are_valid(tmp_path):
    store = ReceiptStore(tmp_path / "receipts.sqlite3")
    store.append(receipt(ActionStatus.REQUESTED, "r1"))
    store.append(receipt(ActionStatus.AUTHORIZED, "r2"))
    store.append(receipt(ActionStatus.DISPATCHED, "r3"))
    store.append(receipt(ActionStatus.SUCCEEDED, "r4"))
    rows = store.list_owned("human-1")
    assert [row["status"] for row in reversed(rows)] == [
        "requested", "authorized", "dispatched", "succeeded"
    ]


def test_illegal_transition_is_rejected(tmp_path):
    store = ReceiptStore(tmp_path / "receipts.sqlite3")
    store.append(receipt(ActionStatus.REQUESTED, "r1"))
    with pytest.raises(ValueError):
        store.append(receipt(ActionStatus.SUCCEEDED, "r2"))


def test_receipt_reads_are_actor_scoped(tmp_path):
    store = ReceiptStore(tmp_path / "receipts.sqlite3")
    store.append(receipt(ActionStatus.REQUESTED, "r1"))
    assert store.list_owned("other-human") == []
