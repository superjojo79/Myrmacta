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


import assemble  # noqa: E402
import voice  # noqa: E402


def test_cuts_disappear_and_fast_passages_are_compressed():
    parts = assemble.segments([{"start": 2, "end": 10, "kind": "cut"}, {"start": 12, "end": 20, "kind": "fast", "factor": 4}], 25)
    assert parts == [(0.0, 2, 1.0), (10, 12, 1.0), (12, 20, 4.0), (20, 25, 1.0)]
    assert assemble.screen_length(parts) == 2 + 2 + 2 + 5


def test_a_chapter_without_spans_is_kept_whole():
    assert assemble.segments([], 12.5) == [(0.0, 12.5, 1.0)]


def test_cues_follow_the_lead_silence_and_the_pauses():
    cues = voice.cues(["Un.", "Deux."], [2.0, 3.0])
    assert cues[0] == {"start": voice.LEAD, "end": voice.LEAD + 2.0, "text": "Un."}
    assert cues[1]["start"] == round(voice.LEAD + 2.0 + voice.PAUSE, 3)


def test_subtitles_and_chapters_are_well_formed():
    vtt, srt = assemble.subtitles([{"start": 0.6, "end": 3.25, "text": "Bonjour."}, {"start": 61.0, "end": 62.5, "text": "Fin."}])
    assert vtt.startswith("WEBVTT\n\n00:00:00.600 --> 00:00:03.250\nBonjour.")
    assert "2\n00:01:01,000 --> 00:01:02,500\nFin." in srt
    meta = assemble.ffmetadata("Capsule", [("Intro", 0, 12.5), ("Facture", 12.5, 40)])
    assert meta.startswith(";FFMETADATA1\ntitle=Capsule")
    assert "START=12500\nEND=40000\ntitle=Facture" in meta


def test_a_fast_span_needs_a_positive_factor():
    import pytest

    with pytest.raises(ValueError):
        assemble.segments([{"start": 1, "end": 2, "kind": "fast", "factor": 0}], 5)


def test_ffmpeg_inputs_are_escaped():
    assert assemble.metadata_value("A=B;C#D\\E\nF") == "A\\=B\\;C\\#D\\\\E\\\nF"
    assert assemble.concat_line(Path("/tmp/l'atelier/x.mp4")).endswith("l'\\''atelier/x.mp4'\n")


def test_the_filmed_frame_must_be_16_by_9():
    import pytest

    assemble.check_frame(1280, 720)
    assemble.check_frame(1920, 1080)
    with pytest.raises(SystemExit):
        assemble.check_frame(1280, 800)


def test_without_out_each_capsule_writes_to_its_own_folder():
    # TODO §136 : même dossier que le tournage (FILM_OUT par défaut) ; deux capsules ne mélangent pas leurs sorties.
    capsules = Path(__file__).parent / "capsules"
    for module in (voice, assemble):
        assert module.default_out(capsules / "l2-onboarding.json") == Path("frontend/capture/out/film/l2-onboarding")
        assert module.default_out(capsules / "l1-mailroom.json") == Path("frontend/capture/out/film/l1-mailroom")


# ------------------------------------------------------------------ Capsules courtes (TODO §140, D-VIDEO-SHORT, #58)


def test_a_caption_stays_on_screen_for_its_reading_time_never_less_than_the_minimum():
    long_text = "x" * 60
    cues = voice.reading_cues(["Court.", long_text])
    assert cues[0] == {"start": voice.LEAD, "end": round(voice.LEAD + voice.MIN_READ, 3), "text": "Court."}
    assert cues[1]["start"] == round(cues[0]["end"] + voice.PAUSE, 3)
    assert cues[1]["end"] - cues[1]["start"] == round(60 / voice.READ_CPS, 3)


def test_a_short_capsule_is_timed_without_speaking(tmp_path, monkeypatch):
    import asyncio
    import json

    async def no_speech(*_args):
        raise AssertionError("a short capsule must not call edge-tts")

    monkeypatch.setattr(voice, "speak", no_speech)
    capsule = tmp_path / "c0-test.json"
    capsule.write_text(json.dumps({
        "id": "c0-test", "format": "short", "title": {"fr": "T", "en": "T"},
        "chapters": [{"id": "plan", "title": {"fr": "P", "en": "P"}, "captions": {"fr": ["Un sous-titre."], "en": ["A caption."]}}],
    }), encoding="utf-8")
    assert asyncio.run(voice.run(capsule, "en", tmp_path / "out")) == 0
    report = json.loads((tmp_path / "out" / "en" / "voice.json").read_text(encoding="utf-8"))
    chapter = report["chapters"]["plan"]
    assert chapter["cues"][0]["text"] == "A caption."
    assert chapter["duration"] == round(chapter["cues"][-1]["end"] + voice.TAIL, 3)
    assert not (tmp_path / "out" / "en" / "voice").exists()


def test_burned_subtitles_read_a_plain_file_name_never_a_path_to_escape():
    import pytest

    burn = assemble.burn_filter("c1-dossier-fr.srt")
    assert burn.startswith("subtitles=filename=c1-dossier-fr.srt:force_style='")
    assert "Fontsize=" in burn and "BorderStyle=" in burn
    for unsafe in ("C:/films/c1.srt", "a'b.srt", "x.srt,drawtext=text=1"):
        with pytest.raises(ValueError):
            assemble.burn_filter(unsafe)


def test_a_short_chapter_has_no_audio_and_lasts_at_least_its_captions():
    command = assemble.chapter_command(Path("v.webm"), [(0.0, 4.0, 1.0)], None, 6.5, Path("c.mp4"))
    assert "-an" in command and "[a]" not in " ".join(command)
    assert command[command.index("-t") + 1] == "6.500"
    assert "tpad=stop_mode=clone:stop_duration=2.500" in command[command.index("-filter_complex") + 1]
    spoken = assemble.chapter_command(Path("v.webm"), [(0.0, 4.0, 1.0)], Path("v.m4a"), 6.5, Path("c.mp4"))
    assert "-c:a" in spoken and "-an" not in spoken


def test_the_capsules_are_well_formed():
    import json

    for path in sorted((Path(__file__).parent / "capsules").glob("*.json")):
        capsule = json.loads(path.read_text(encoding="utf-8"))
        assert capsule["id"] == path.stem
        key = "captions" if capsule.get("format") == "short" else "narration"
        if key == "narration":
            assert set(capsule["voices"]) == {"fr", "en"}
        else:
            assert set(capsule["underTheHood"]) == {"fr", "en"}, path.name
        for chapter in capsule["chapters"]:
            assert len(chapter[key]["fr"]) == len(chapter[key]["en"]), f"{path.name} {chapter['id']}"
