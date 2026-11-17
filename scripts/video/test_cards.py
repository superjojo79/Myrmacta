"""Tests des cartons du teaser (TODO §148, D-VIDEO-REMOTION, issue #59).

    uv run --with pytest --with pyjwt pytest scripts/video -q
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent))

import cards  # noqa: E402

TEASER = Path(__file__).parent / "capsules" / "t00-teaser.json"


def punchline(**overrides) -> dict:
    card = {"id": "flow", "composition": "Punchline", "seconds": 4.0,
            "props": {"word": "flow", "tagline": {"fr": "Décrivez le parcours.", "en": "Describe the journey."}}}
    card.update(overrides)
    return card


def test_a_localized_text_is_taken_in_the_requested_language_and_the_rest_is_kept_as_is():
    assert cards.card_props(punchline(), "en") == {"word": "flow", "tagline": "Describe the journey.", "seconds": 4.0}
    assert cards.card_props(punchline(), "fr")["tagline"] == "Décrivez le parcours."


def test_a_localized_list_is_taken_whole_in_the_requested_language():
    closing = {"id": "closing", "composition": "Closing", "seconds": 9.0,
               "props": {"lines": {"fr": ["Un.", "Deux."], "en": ["One.", "Two."]}, "url": "myrmacta.be"}}
    assert cards.card_props(closing, "en") == {"lines": ["One.", "Two."], "url": "myrmacta.be", "seconds": 9.0}


def test_a_text_missing_in_one_language_is_refused_with_the_card_and_the_language():
    card = punchline(props={"word": "flow", "tagline": {"fr": "Décrivez le parcours."}})
    with pytest.raises(ValueError, match=r"flow.*tagline.*en"):
        cards.check_card(card)


def test_a_card_too_short_to_read_its_text_is_refused():
    card = punchline(seconds=1.5, props={"word": "flow", "tagline": {"fr": "x" * 60, "en": "y" * 60}})
    with pytest.raises(ValueError, match=r"flow.*1\.5"):
        cards.check_card(card)


def test_the_reading_time_counts_from_when_the_text_appears():
    # Punchline : l'accroche arrive après la frappe du mot ; le carton doit la tenir le temps de la lire.
    card = punchline(props={"word": "flow", "tagline": {"fr": "x" * 30, "en": "y" * 30}})
    assert cards.minimum_seconds(card) == pytest.approx(0.45 + 4 * 0.07 + 0.15 + 0.6 + 2.2 + 0.4)


def test_an_unknown_composition_is_refused():
    with pytest.raises(ValueError, match="Intro"):
        cards.check_card(punchline(composition="Intro"))


def test_the_render_command_passes_the_props_file_the_brand_folder_and_the_browser(tmp_path):
    command = cards.render_command(punchline(), Path("props.json"), Path("out/flow.mp4"), Path("chrome.exe"))
    assert command[1].endswith("remotion-cli.js")
    assert command[2:6] == ["render", "src/index.ts", "Punchline", str(Path("out/flow.mp4").resolve())]
    assert f"--props={Path('props.json').resolve()}" in command
    assert "--browser-executable=" + str(Path("chrome.exe")) in command
    assert "--muted" in command and "--codec=h264" in command and "--pixel-format=yuv420p" in command
    assert any(arg.startswith("--public-dir=") and arg.endswith(str(Path("frontend/src/assets/brand")))
               for arg in command)


def test_the_newest_playwright_headless_shell_is_chosen(tmp_path):
    for version in ("1200", "1234"):
        exe = tmp_path / f"chromium_headless_shell-{version}" / "chrome-headless-shell-win64" / "chrome-headless-shell.exe"
        exe.parent.mkdir(parents=True)
        exe.write_text("")
    assert cards.find_headless_shell([tmp_path]).parent.parent.name == "chromium_headless_shell-1234"


def test_without_a_headless_shell_the_message_says_how_to_install_it(tmp_path):
    with pytest.raises(SystemExit, match="playwright install chromium-headless-shell"):
        cards.find_headless_shell([tmp_path])


def test_the_teaser_file_has_five_valid_cards_with_the_same_punchline_words_in_both_languages():
    teaser = json.loads(TEASER.read_text(encoding="utf-8"))
    assert [c["id"] for c in teaser["cards"]] == ["opening", "flow", "decide", "sign", "closing"]
    for card in teaser["cards"]:
        cards.check_card(card)
    assert [c["props"]["word"] for c in teaser["cards"] if c["composition"] == "Punchline"] == ["flow", "decide", "sign"]
