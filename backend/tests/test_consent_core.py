from datetime import datetime, timedelta, timezone

import pytest

from app.consent_core import Access, ConsentError, Duration, Grant, authorize, normalize_access

NOW = datetime(2026, 9, 20, 12, 0, tzinfo=timezone.utc)


def grant(**overrides):
    values = dict(
        subject_id="user-1",
        resource="workflow:image-basic",
        access=frozenset({Access.WRITE}),
        duration=Duration.TASK,
        created_at=NOW,
        expires_at=NOW + timedelta(hours=1),
        task_id="task-1",
    )
    values.update(overrides)
    return Grant(**values)


def test_exact_scoped_task_grant_authorizes():
    authorize(grant(), subject_id="user-1", resource="workflow:image-basic",
              access=Access.WRITE, now=NOW, task_id="task-1")


@pytest.mark.parametrize("changes", [
    {"subject_id": "user-2"},
    {"resource": "workflow:video"},
])
def test_subject_or_resource_mismatch_denied(changes):
    with pytest.raises(ConsentError):
        authorize(grant(), subject_id=changes.get("subject_id", "user-1"),
                  resource=changes.get("resource", "workflow:image-basic"),
                  access=Access.WRITE, now=NOW, task_id="task-1")


def test_missing_access_level_denied():
    with pytest.raises(ConsentError):
        authorize(grant(), subject_id="user-1", resource="workflow:image-basic",
                  access=Access.DELETE, now=NOW, task_id="task-1")


def test_expired_or_revoked_grant_denied():
    expired = grant(expires_at=NOW)
    revoked = grant(revoked_at=NOW)
    for item in (expired, revoked):
        with pytest.raises(ConsentError):
            authorize(item, subject_id="user-1", resource="workflow:image-basic",
                      access=Access.WRITE, now=NOW, task_id="task-1")


def test_task_scope_cannot_be_reused_for_another_task():
    with pytest.raises(ConsentError):
        authorize(grant(), subject_id="user-1", resource="workflow:image-basic",
                  access=Access.WRITE, now=NOW, task_id="task-2")


def test_once_grant_must_match_session_and_task_and_be_unconsumed():
    item = grant(duration=Duration.ONCE, session_id="session-1", task_id="task-1")
    authorize(item, subject_id="user-1", resource="workflow:image-basic",
              access=Access.WRITE, now=NOW, session_id="session-1", task_id="task-1")
    with pytest.raises(ConsentError):
        authorize(item, subject_id="user-1", resource="workflow:image-basic",
                  access=Access.WRITE, now=NOW, session_id="session-1", task_id="task-1",
                  consumed_once=True)


def test_unknown_access_is_rejected():
    with pytest.raises(ValueError):
        normalize_access(["write", "admin"])
