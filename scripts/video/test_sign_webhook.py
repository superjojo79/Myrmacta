"""Tests du rejeu du webhook Yousign du tournage C13 (TODO §148, D-VIDEO-SIGN-WEBHOOK, issue #59).

    uv run --with pytest --with pyjwt pytest scripts/video -q
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent))

import sign_webhook  # noqa: E402

REQUEST_ID = "0b5f2c3e-6f4a-4d8e-9a51-2f0c7e1d4b6a"


def test_signature_isTheHmacSha256OfTheRawBody_likeTheJavaAuthenticatorTest():
    # Même corps et même secret que WebhookAuthenticatorTest.yousignAuthenticator_* ; valeur calculée par openssl.
    body = b'{"event_id":"b6c63685-c556-4a30-8fe9-b6f2b187d936","event_name":"signature_request.done"}'
    assert sign_webhook.signature("webhook-secret", body) == (
        "sha256=c83f2560aadfb440892a2ae730ebd92ea1f083bd25cbded19f08b6a3cea56a3a")


def test_event_isASignatureRequestDone_carryingTheRequestReadFromYousign():
    request = {"id": REQUEST_ID, "status": "done", "name": "Convention"}
    body = json.loads(sign_webhook.event(request, "b6c63685-c556-4a30-8fe9-b6f2b187d936", 1_800_000_000))
    assert body["event_id"] == "b6c63685-c556-4a30-8fe9-b6f2b187d936"
    assert body["event_name"] == "signature_request.done"
    assert body["event_time"] == "1800000000"
    assert body["sandbox"] is True
    assert body["data"]["signature_request"] == request


@pytest.mark.parametrize("status", ["ongoing", "draft", "declined", "expired", "canceled"])
def test_aRequestThatIsNotDoneInYousign_isNotReplayed(status):
    with pytest.raises(SystemExit, match=f"{REQUEST_ID}.*{status}"):
        sign_webhook.ensure_done({"id": REQUEST_ID, "status": status})


def test_theRequestToReplay_isTheLatestDoneOne_whenNoneIsGiven():
    page = {"data": [{"id": "a", "status": "done"}, {"id": "b", "status": "done"}]}
    assert sign_webhook.latest_done(page) == "a"


def test_noDoneRequestInYousign_saysWhatToDo():
    with pytest.raises(SystemExit, match="aucune demande signée"):
        sign_webhook.latest_done({"data": []})


def test_theCallbackUrl_isTheEndpointOfTheSignatureConnector():
    assert sign_webhook.callback_url("http://localhost:8080/", "4c1d") == "http://localhost:8080/api/callbacks/4c1d"


def test_missingKeys_areNamed(monkeypatch):
    monkeypatch.delenv("YOUSIGN_SANDBOX_API_KEY", raising=False)
    monkeypatch.setenv("YOUSIGN_WEBHOOK_SECRET", "s")
    with pytest.raises(SystemExit, match="YOUSIGN_SANDBOX_API_KEY"):
        sign_webhook.keys()
