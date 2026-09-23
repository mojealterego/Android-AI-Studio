import json

import pytest

from app.approval_auth import ApprovalAuthenticationError, authenticate_approver


def test_approval_auth_never_accepts_client_subject(monkeypatch):
    monkeypatch.setenv(
        "APPROVAL_TOKENS_JSON",
        json.dumps({"human-1": "0123456789abcdef0123456789abcdef"}),
    )
    assert authenticate_approver("0123456789abcdef0123456789abcdef") == "human-1"


def test_missing_or_wrong_approval_credential_fails_closed(monkeypatch):
    monkeypatch.setenv(
        "APPROVAL_TOKENS_JSON",
        json.dumps({"human-1": "0123456789abcdef0123456789abcdef"}),
    )
    for token in (None, "wrong-token"):
        with pytest.raises(ApprovalAuthenticationError):
            authenticate_approver(token)


def test_unconfigured_approval_boundary_fails_closed(monkeypatch):
    monkeypatch.delenv("APPROVAL_TOKENS_JSON", raising=False)
    with pytest.raises(ApprovalAuthenticationError):
        authenticate_approver("0123456789abcdef0123456789abcdef")
