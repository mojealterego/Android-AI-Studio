from datetime import datetime, timezone

import pytest

from app.consent_audit_store import ConsentAuditStore
from app.consent_receipt import ActionReceipt, ActionStatus


def receipt(status: ActionStatus, *, receipt_id: str = "r-1") -> ActionReceipt:
    return ActionReceipt(
        receipt_id=receipt_id,
        actor_id="principal-1",
        action_type="workflow.execute",
        resource="workflow:abc123",
        status=status,
        occurred_at=datetime(2026, 9, 20, 12, 0, tzinfo=timezone.utc),
        grant_id="g-1",
        task_id="t-1",
        request_digest="sha256:deadbeef",
    )


def test_append_and_read_events_in_order(tmp_path):
    store = ConsentAuditStore(tmp_path / "audit.sqlite")
    first = store.append(receipt(ActionStatus.AUTHORIZED))
    second = store.append(receipt(ActionStatus.DISPATCHED))
    events = store.get_events("r-1")
    assert [event["event_id"] for event in events] == [first, second]
    assert [event["status"] for event in events] == ["authorized", "dispatched"]


def test_list_events_is_actor_scoped_and_newest_first(tmp_path):
    store = ConsentAuditStore(tmp_path / "audit.sqlite")
    store.append(receipt(ActionStatus.AUTHORIZED))
    store.append(receipt(ActionStatus.SUCCEEDED))
    other = ActionReceipt(
        receipt_id="r-2", actor_id="principal-2", action_type="workflow.execute",
        resource="workflow:def456", status=ActionStatus.SUCCEEDED,
        occurred_at=datetime(2026, 9, 20, 12, 1, tzinfo=timezone.utc),
    )
    store.append(other)
    events = store.list_events(actor_id="principal-1", limit=1)
    assert len(events) == 1
    assert events[0]["status"] == "succeeded"


@pytest.mark.parametrize("limit", [0, 501, True, 1.5])
def test_rejects_invalid_limit(tmp_path, limit):
    store = ConsentAuditStore(tmp_path / "audit.sqlite")
    with pytest.raises(ValueError):
        store.list_events(actor_id="principal-1", limit=limit)


def test_rejects_blank_actor(tmp_path):
    store = ConsentAuditStore(tmp_path / "audit.sqlite")
    with pytest.raises(ValueError):
        store.list_events(actor_id=" ")
