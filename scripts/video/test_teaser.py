"""Tests du montage du teaser (TODO §148, D-VIDEO-TEASER, issue #59).

    uv run --with pytest --with pyjwt pytest scripts/video -q
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent))

import teaser  # noqa: E402

TEASER = Path(__file__).parent / "capsules" / "t00-teaser.json"

SOURCE_CUES = [
    {"start": 0.6, "end": 4.6, "text": "Premier."},
    {"start": 5.05, "end": 9.45, "text": "Deuxième."},
    {"start": 9.9, "end": 14.0, "text": "Troisième."},
]


def cut(first=1, last=1, fr=None, en=None) -> dict:
    count = last - first + 1
    return {"cut": {"capsule": "c08-panne", "plan": "rejeu", "cues": [first, last]},
            "captions": {"fr": fr or ["Un clic rejoue l'étape."] * count, "en": en or ["One click replays it."] * count}}


def film(tmp_path: Path, lang: str = "fr", plans: tuple[str, ...] = ("01-incident", "02-rejeu")) -> Path:
    folder = tmp_path / "c08-panne" / lang
    (folder / "clips").mkdir(parents=True)
    for name in plans:
        (folder / "clips" / f"{name}.mp4").write_bytes(b"")
    chapters = {name.split("-", 1)[1]: {"duration": 14.4, "cues": SOURCE_CUES} for name in plans}
    (folder / "voice.json").write_text(json.dumps({"chapters": chapters}), encoding="utf-8")
    return tmp_path


def test_a_cut_spans_its_cues_with_a_lead_in_and_a_tail():
    assert teaser.cut_window(SOURCE_CUES, 1, 2, 14.6) == pytest.approx((5.05 - 0.3, 14.0 + 0.4))


def test_a_cut_stops_at_the_end_of_its_plan():
    assert teaser.cut_window(SOURCE_CUES, 2, 2, 14.2)[1] == pytest.approx(14.2)


def test_a_cut_can_skip_the_start_of_its_plan_and_its_first_caption_waits_for_the_image():
    # Plan d'une capsule longue : il s'ouvre sur un carton de chapitre, que le teaser ne montre pas.
    assert teaser.cut_window(SOURCE_CUES, 0, 0, 14.6, skip=2.6) == pytest.approx((2.6, 4.6 + 0.4))
    item = cut(first=0, last=0)
    item["cut"]["from"] = 2.6
    cues = teaser.cut_cues(item, SOURCE_CUES, "fr", start=2.6, offset=10.0)
    assert (cues[0]["start"], cues[0]["end"]) == (pytest.approx(10.0), pytest.approx(12.0))


def test_a_caption_must_be_readable_in_what_remains_of_its_cue_after_the_skipped_start():
    item = cut(first=0, last=0, fr=["x" * 40])  # 2,7 s de lecture ; le repère 0,6–4,6 s, vu à partir de 2,6 s : 2 s
    item["cut"]["from"] = 2.6
    with pytest.raises(ValueError, match=r"c08-panne.*rejeu.*fr.*2\.0 s"):
        teaser.check_captions(item, SOURCE_CUES, "fr")


def test_a_cue_outside_the_plan_is_refused():
    with pytest.raises(ValueError, match=r"repère 3"):
        teaser.cut_window(SOURCE_CUES, 1, 3, 14.6)


def test_a_cut_shows_its_captions_on_the_timing_of_the_cues_it_replaces():
    item = cut(first=1, last=2, fr=["Un clic rejoue l'étape.", "Le dossier va au bout."])
    cues = teaser.cut_cues(item, SOURCE_CUES, "fr", start=4.75, offset=20.0)
    assert cues == [
        {"start": pytest.approx(20.0 + 0.3), "end": pytest.approx(20.0 + 0.3 + 4.4), "text": "Un clic rejoue l'étape."},
        {"start": pytest.approx(20.0 + 4.85 + 0.3), "end": pytest.approx(20.0 + 4.85 + 0.3 + 4.1),
         "text": "Le dossier va au bout."},
    ]


def test_a_caption_too_long_to_read_in_its_cue_is_refused_with_the_capsule_the_plan_and_the_language():
    item = cut(en=["x" * 80])  # 80 caractères : 5,3 s de lecture pour un repère de 4,4 s
    with pytest.raises(ValueError, match=r"c08-panne.*rejeu.*en"):
        teaser.check_captions(item, SOURCE_CUES, "en")


def test_the_caption_of_the_capsule_itself_fits_its_cue_despite_the_millisecond_rounding_of_voice_json():
    # Lot 4 : C13 « envoi », repère 1 minuté par voice.py sur ce même texte (56 caractères, 3,7333 s), arrondi à 3,733 s.
    text = "Le document part en signature électronique chez Yousign."
    cues = [{"start": 0.6, "end": 3.133, "text": "a"}, {"start": 3.583, "end": 7.316, "text": text}]
    item = {"cut": {"capsule": "c13-signature", "plan": "envoi", "cues": [1, 1]}, "captions": {"fr": [text], "en": [text]}}
    teaser.check_captions(item, cues, "fr")


def test_one_caption_per_cue_is_required():
    with pytest.raises(ValueError, match=r"c08-panne.*rejeu.*fr.*2 repères"):
        teaser.check_captions(cut(first=1, last=2, fr=["Un seul."]), SOURCE_CUES, "fr")


def test_a_plan_is_found_by_its_id_whatever_its_number(tmp_path):
    path, cues = teaser.plan_source(film(tmp_path), "c08-panne", "rejeu", "fr")
    assert path.name == "02-rejeu.mp4"
    assert cues == SOURCE_CUES


def test_a_plan_missing_in_a_language_gives_the_command_that_films_it(tmp_path):
    with pytest.raises(SystemExit, match=r"APP_DEMO_LOCALE=en scripts/video.sh capsule c08-panne"):
        teaser.plan_source(film(tmp_path, "fr"), "c08-panne", "rejeu", "en")


def test_a_teaser_longer_than_90_seconds_is_refused():
    with pytest.raises(ValueError, match=r"91\.0 s"):
        teaser.check_total(91.0)
    teaser.check_total(90.0)


def test_an_unknown_card_in_the_sequence_is_refused():
    with pytest.raises(ValueError, match="intro"):
        teaser.card_of({"cards": [{"id": "flow"}]}, "intro")


def test_the_teaser_sequence_opens_with_the_opening_card_and_ends_with_the_closing_card():
    data = json.loads(TEASER.read_text(encoding="utf-8"))
    sequence = data["sequence"]
    assert sequence[0] == {"card": "opening"} and sequence[-1] == {"card": "closing"}
    assert [item["card"] for item in sequence if "card" in item][1:3] == ["flow", "decide"]
    for item in sequence:
        if "card" in item:
            teaser.card_of(data, item["card"])
        else:
            first, last = item["cut"]["cues"]
            assert len(item["captions"]["fr"]) == len(item["captions"]["en"]) == last - first + 1
