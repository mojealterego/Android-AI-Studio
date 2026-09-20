from datetime import datetime, timedelta, timezone

import pytest

from app.consent_receipt import ActionReceipt, ActionStatus


def receipt(**overrides):
    values = dict(
        receipt_id="r-1",
        actor_id="user-1",
        action_type="workflow.dispatch",
        resource="workflow:test:sha256:abc",
        status=ActionStatus.AUTHORIZED,
        occurred_at=datetime(2026, 9, 20, 12, tzinfo=timezone(timedelta(hours=2))),
        grant_id="g-1",
    )
    values.update(overrides)
    return ActionReceipt(**values)


def test_to_dict_uses_utc_and_string_status():
    data = receipt().to_dict()
    assert data["status"] == "authorized"
    assert data["occurred_at"] == "2026-09-20T10:00:00+00:00"
    assert data["grant_id"] == "g-1"


def test_rejects_naive_timestamp():
    with pytest.raises(ValueError, match="timezone-aware"):
        receipt(occurred_at=datetime(2026, 9, 20, 12))


@pytest.mark.parametrize("field", ["receipt_id", "actor_id", "action_type", "resource"])
def test_rejects_missing_required_fields(field):
    with pytest.raises(ValueError, match="required"):
        receipt(**{field: "   "})


def test_rejects_oversized_field():
    with pytest.raises(ValueError, match="exceeds"):
        receipt(provenance="x" * 2049)
