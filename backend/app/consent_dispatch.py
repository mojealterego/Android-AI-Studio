"""Fail-closed consent guard for protected workflow dispatch.

This module deliberately does not create or approve grants. The caller must
obtain a grant through a separately authenticated human approval flow, then
call ``authorize_dispatch`` immediately before the external side effect.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .consent_binding import workflow_resource
from .consent_core import Access, ConsentError
from .consent_store import ConsentStore


@dataclass(frozen=True)
class DispatchAuthorization:
    grant_id: str
    subject_id: str
    workflow_id: str
    resource: str
    task_id: str | None = None
    session_id: str | None = None


def authorize_dispatch(
    store: ConsentStore,
    authorization: DispatchAuthorization,
    parameters: dict[str, Any],
) -> None:
    """Validate exact workflow+parameters and consume one-shot grant.

    ``subject_id`` must come from trusted authentication context, never from
    an agent-controlled request field. ``parameters`` must be the same object
    used to build the dispatched graph; callers must not mutate it afterward.
    """
    expected_resource = workflow_resource(authorization.workflow_id, parameters)
    if authorization.resource != expected_resource:
        raise ConsentError("Consent does not match this exact workflow preview")
    store.authorize_and_consume_once(
        authorization.grant_id,
        subject_id=authorization.subject_id,
        resource=expected_resource,
        access=Access.WRITE,
        session_id=authorization.session_id,
        task_id=authorization.task_id,
    )
