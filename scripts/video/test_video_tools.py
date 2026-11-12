"""Tests de l'outillage de la capsule vidéo (TODO §128, issue #56).

    uv run --with pytest --with pyjwt pytest scripts/video -q
"""

from __future__ import annotations

import sys
from pathlib import Path
from types import SimpleNamespace

import jwt

sys.path.insert(0, str(Path(__file__).parent))

import agent  # noqa: E402
import send_mail  # noqa: E402


def test_the_m2m_token_carries_the_agent_subject_the_api_service_role_and_the_tenant():
    token = agent.m2m_token("dev-secret-of-at-least-32-bytes!!", "agent-ia-tri", "tenant_dev", ttl_seconds=60)
    claims = jwt.decode(token, "dev-secret-of-at-least-32-bytes!!", algorithms=["HS256"])
    assert claims["sub"] == "agent-ia-tri"
    assert claims["roles"] == ["API_SERVICE"]
    assert claims["tenant_id"] == "tenant_dev"
    assert claims["exp"] - claims["iat"] == 60


def test_an_mcp_tool_becomes_an_openai_function_with_an_object_schema():
    tool = SimpleNamespace(name="get_mail_thread", description="Mail thread",
                           inputSchema={"properties": {"dossierId": {"type": "string"}}, "required": ["dossierId"]})
    converted = agent.openai_tool(tool)
    assert converted["type"] == "function"
    assert converted["function"]["name"] == "get_mail_thread"
    assert converted["function"]["parameters"]["type"] == "object"
    assert converted["function"]["parameters"]["required"] == ["dossierId"]
    assert agent.openai_tool(SimpleNamespace(name="x", description=None, inputSchema=None))["function"]["parameters"] == {
        "type": "object", "properties": {}}


def test_malformed_tool_arguments_from_the_model_become_an_empty_call():
    assert agent.parse_arguments('{"dryRun": true}') == {"dryRun": True}
    assert agent.parse_arguments("not json") == {}
    assert agent.parse_arguments("[1, 2]") == {}
    assert agent.parse_arguments(None) == {}


def test_a_long_tool_result_is_bounded():
    result = SimpleNamespace(content=[SimpleNamespace(text="x" * 10_000)], structuredContent=None)
    assert len(agent.result_text(result)) <= agent.MAX_RESULT_CHARS + 2


def test_the_invoice_is_attested_and_the_reply_joins_its_thread():
    invoice = send_mail.message("facture", "fr")
    reply = send_mail.message("reponse", "en")
    assert invoice["Authentication-Results"].startswith(send_mail.TRUSTED_AUTHSERV_ID + ";")
    assert invoice["Message-ID"] == send_mail.INVOICE_ID
    assert reply["In-Reply-To"] == send_mail.INVOICE_ID
    assert reply["Subject"] == "Re: Invoice 2026-117"
    attachments = [part.get_filename() for part in invoice.iter_attachments()]
    assert attachments == ["facture-2026-117.pdf"]
    assert send_mail.pdf("Facture (2026)").startswith(b"%PDF-1.4")


def test_the_unknown_mail_comes_from_a_sender_the_routing_table_does_not_know():
    unknown = send_mail.message("inconnu", "fr")
    assert unknown["From"].split("@")[1] != "fournisseur.example"
    assert "2026-117" in unknown["Subject"]
