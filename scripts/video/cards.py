# /// script
# requires-python = ">=3.11"
# ///
"""Cartons du teaser (TODO §148, D-VIDEO-REMOTION, issue #59).

Lit les cartons d'un teaser (`"cards"` de scripts/video/capsules/t00-teaser.json) et rend chacun en MP4 avec Remotion
(projet scripts/video/remotion) : 1920×1080, 30 i/s, H.264, sans son, dans la langue demandée.

    uv run scripts/video/cards.py scripts/video/capsules/t00-teaser.json --lang fr
    → frontend/capture/out/film/t00-teaser/<lang>/cards/<id>.mp4

Un texte se donne dans les deux langues ({"fr": …, "en": …}) ; les autres valeurs (le mot de la punchline, l'adresse
du site) sont les mêmes partout. Chaque carton dure au moins le temps d'apparaître puis d'être lu (règle des capsules
courtes, voice.py) : sinon le rendu est refusé avant de lancer Remotion.

Navigateur : le Chromium headless de Playwright, déjà installé pour les captures (`npx playwright install
chromium-headless-shell` dans frontend). Edge, essayé d'abord, ouvre une vraie fenêtre et met plusieurs minutes par
image. REMOTION_BROWSER désigne un autre exécutable.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

from voice import MIN_READ, READ_CPS, TAIL

LANGS = ("fr", "en")
REPO = Path(__file__).resolve().parents[2]
PROJECT = Path(__file__).resolve().parent / "remotion"
BRAND = REPO / "frontend" / "src" / "assets" / "brand"

# Quand le texte d'un carton commence à apparaître (s) et combien dure son apparition : les mêmes valeurs que
# remotion/src/Cards.tsx (Rise à 1.0 s dans Opening ; frappe du mot à partir de 0.45 s, 0.07 s par lettre, puis
# l'accroche 0.15 s après ; lignes de Closing à 0.9 s puis toutes les 0.7 s).
REVEAL = 0.6
TYPING_START = 0.45
PER_CHAR = 0.07
TAGLINE_GAP = 0.15
OPENING_LINE = 1.0
CLOSING_FIRST = 0.9
CLOSING_STEP = 0.7

COMPOSITIONS = ("Opening", "Punchline", "Closing")


def localized(value: object) -> bool:
    return isinstance(value, dict) and set(value) & set(LANGS) != set()


def card_props(card: dict, lang: str) -> dict:
    """Les props Remotion d'un carton dans `lang` : chaque texte {fr, en} pris dans la langue, plus `seconds`."""
    props = {key: value[lang] if localized(value) else value for key, value in card["props"].items()}
    return {**props, "seconds": card["seconds"]}


def reading(text: str) -> float:
    return max(MIN_READ, len(text) / READ_CPS)


def minimum_seconds(card: dict) -> float:
    """Durée minimale d'un carton : son texte apparaît, puis reste le temps d'être lu (la plus longue langue)."""
    props = card["props"]
    needed = []
    for lang in LANGS:
        if card["composition"] == "Opening":
            needed.append(OPENING_LINE + REVEAL + reading(props["line"][lang]))
        elif card["composition"] == "Punchline":
            appears = TYPING_START + len(props["word"]) * PER_CHAR + TAGLINE_GAP
            needed.append(appears + REVEAL + reading(props["tagline"][lang]))
        else:
            lines = props["lines"][lang]
            last = CLOSING_FIRST + (len(lines) - 1) * CLOSING_STEP
            needed.append(max(CLOSING_FIRST + REVEAL + reading(" ".join(lines)), last + REVEAL + reading(lines[-1])))
    return max(needed) + TAIL


def check_card(card: dict) -> None:
    """Refuse un carton mal formé : composition inconnue, texte absent d'une langue, durée trop courte pour le lire."""
    name = card.get("id", "?")
    if card.get("composition") not in COMPOSITIONS:
        raise ValueError(f"carton {name} : composition inconnue {card.get('composition')!r} (attendu : {COMPOSITIONS})")
    for key, value in card["props"].items():
        if isinstance(value, dict):
            for lang in LANGS:
                if not value.get(lang):
                    raise ValueError(f"carton {name} : {key} manque en {lang}")
    minimum = minimum_seconds(card)
    if card["seconds"] + 1e-9 < minimum:
        raise ValueError(f"carton {name} : {card['seconds']} s, trop court pour lire son texte (au moins {minimum:.1f} s)")


def find_headless_shell(roots: list[Path]) -> Path:
    """Le Chromium headless de Playwright le plus récent sous `roots` (dossiers ms-playwright)."""
    found = []
    for root in roots:
        for exe in root.glob("chromium_headless_shell-*/chrome-headless-shell-*/chrome-headless-shell*"):
            match = re.search(r"chromium_headless_shell-(\d+)", str(exe))
            if match and exe.suffix in ("", ".exe"):
                found.append((int(match.group(1)), exe))
    if not found:
        raise SystemExit("Chromium headless introuvable : lancer « cd frontend && npx playwright install "
                         "chromium-headless-shell », ou désigner un navigateur par REMOTION_BROWSER")
    return max(found)[1]


def playwright_roots() -> list[Path]:
    """Où Playwright range ses navigateurs : PLAYWRIGHT_BROWSERS_PATH, sinon l'emplacement par défaut du système."""
    if os.environ.get("PLAYWRIGHT_BROWSERS_PATH"):
        return [Path(os.environ["PLAYWRIGHT_BROWSERS_PATH"])]
    roots = [Path.home() / ".cache" / "ms-playwright", Path.home() / "Library" / "Caches" / "ms-playwright"]
    if os.environ.get("LOCALAPPDATA"):
        roots.insert(0, Path(os.environ["LOCALAPPDATA"]) / "ms-playwright")
    return roots


def browser() -> Path:
    override = os.environ.get("REMOTION_BROWSER")
    return Path(override) if override else find_headless_shell(playwright_roots())


def render_command(card: dict, props_file: Path, target: Path, chrome: Path) -> list[str]:
    """La commande Remotion qui rend un carton (lancée dans le projet remotion)."""
    node = shutil.which("node") or "node"
    return [node, str(PROJECT / "node_modules" / "@remotion" / "cli" / "remotion-cli.js"),
            "render", "src/index.ts", card["composition"], str(target.resolve()),
            f"--props={props_file.resolve()}", f"--public-dir={BRAND}", f"--browser-executable={chrome}",
            "--codec=h264", "--pixel-format=yuv420p", "--crf=18", "--muted", "--log=error"]


def ensure_project() -> None:
    """Installe le projet Remotion à la première utilisation (versions exactes du package-lock)."""
    if not (PROJECT / "node_modules" / "@remotion" / "cli").is_dir():
        npm = shutil.which("npm")
        if not npm:
            raise SystemExit("npm introuvable dans le PATH")
        subprocess.run([npm, "ci", "--no-fund", "--no-audit"], check=True, cwd=PROJECT)


def default_out(teaser_path: Path) -> Path:
    teaser_id = json.loads(teaser_path.read_text(encoding="utf-8"))["id"]
    return REPO / "frontend" / "capture" / "out" / "film" / teaser_id


def run(teaser_path: Path, lang: str, out: Path) -> list[Path]:
    teaser = json.loads(teaser_path.read_text(encoding="utf-8"))
    for card in teaser["cards"]:
        check_card(card)
    ensure_project()
    chrome = browser()
    folder = out / lang / "cards"
    folder.mkdir(parents=True, exist_ok=True)
    rendered = []
    for card in teaser["cards"]:
        props_file = folder / f"{card['id']}.props.json"
        props_file.write_text(json.dumps(card_props(card, lang), ensure_ascii=False), encoding="utf-8")
        target = folder / f"{card['id']}.mp4"
        subprocess.run(render_command(card, props_file, target, chrome), check=True, cwd=PROJECT)
        print(f"{target} : {card['composition']}, {card['seconds']} s")
        rendered.append(target)
    return rendered


def main(argv: list[str]) -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("teaser", type=Path)
    parser.add_argument("--lang", choices=LANGS, default="fr")
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args(argv)
    run(args.teaser, args.lang, args.out or default_out(args.teaser))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
