# /// script
# requires-python = ">=3.11"
# ///
"""Montage du teaser (TODO §148, D-VIDEO-TEASER, issue #59).

Lit la suite du teaser (`"sequence"` de scripts/video/capsules/t00-teaser.json) : des cartons, rendus par Remotion
(cards.py), et des coupes prises dans les plans montés des capsules (`<capsule>/<langue>/clips/NN-<plan>.mp4`, sans
sous-titres). Puis tout bout à bout en un MP4 muet, 1920×1080, 30 i/s, sous-titres incrustés.

    uv run scripts/video/teaser.py scripts/video/capsules/t00-teaser.json --lang fr
    → frontend/capture/out/film/t00-teaser/<lang>/t00-teaser-<lang>.mp4, .vtt, .srt

Une coupe désigne des repères de sous-titres de son plan (`"cues": [premier, dernier]`, comptés depuis 0, dans le
voice.json de la capsule) : elle commence juste avant le premier et finit juste après le dernier, dans chaque langue.
Le teaser remplace le texte de chacun de ces repères par le sien, tenu au même moment : l'image montre ce que dit le
sous-titre. `"from"` (s) écarte le début du plan : le carton de chapitre d'une capsule longue. Un texte qui ne se lit pas dans le temps de son repère (règle des capsules courtes, voice.py), une coupe
hors de son plan, ou un teaser de plus de 90 s font échouer le montage avant ffmpeg.

Les cartons sortent de Remotion en pleine échelle (yuvj420p), les plans en échelle limitée : chaque morceau est ramené
en yuv420p échelle limitée avant la concaténation, sinon les couleurs sautent d'un morceau à l'autre.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

import cards
from assemble import FPS, burn_filter, concat_line, probe_duration, subtitles, tool
from voice import TAIL

LANGS = ("fr", "en")
FILM = cards.REPO / "frontend" / "capture" / "out" / "film"
LEAD_IN = 0.3  # une coupe commence un peu avant son premier repère (s)
MAX_SECONDS = 90.0


def card_of(teaser: dict, card_id: str) -> dict:
    for card in teaser["cards"]:
        if card["id"] == card_id:
            return card
    raise ValueError(f"carton inconnu dans la suite du teaser : {card_id}")


def plan_source(film: Path, capsule: str, plan: str, lang: str) -> tuple[Path, list[dict]]:
    """Le plan monté d'une capsule dans une langue, et ses repères de sous-titres."""
    folder = film / capsule / lang
    found = sorted((folder / "clips").glob(f"[0-9][0-9]-{plan}.mp4")) if (folder / "clips").is_dir() else []
    if not found or not (folder / "voice.json").is_file():
        raise SystemExit(f"plan {plan} de {capsule} absent en {lang} : le tourner d'abord "
                         f"(scripts/video.sh start {capsule}, puis APP_DEMO_LOCALE={lang} scripts/video.sh capsule {capsule})")
    voice = json.loads((folder / "voice.json").read_text(encoding="utf-8"))
    return found[0], voice["chapters"][plan]["cues"]


def cut_window(cues: list[dict], first: int, last: int, length: float, skip: float = 0.0) -> tuple[float, float]:
    """Début et fin d'une coupe dans son plan : un peu avant le premier repère (jamais avant `skip`, le début du plan
    écarté), un peu après le dernier."""
    if not 0 <= first <= last < len(cues):
        raise ValueError(f"repère {last if last >= len(cues) else first} hors du plan ({len(cues)} repères)")
    return max(0.0, skip, cues[first]["start"] - LEAD_IN), min(length, cues[last]["end"] + TAIL)


def check_captions(item: dict, cues: list[dict], lang: str) -> None:
    """Un texte par repère remplacé, chacun lisible dans le temps de son repère."""
    source, (first, last) = item["cut"], item["cut"]["cues"]
    where = f"coupe {source['capsule']}/{source['plan']} ({lang})"
    texts = item["captions"][lang]
    if len(texts) != last - first + 1:
        raise ValueError(f"{where} : {len(texts)} texte(s) pour {last - first + 1} repères")
    skip = float(source.get("from", 0.0))
    for text, cue in zip(texts, cues[first:last + 1]):
        slot = cue["end"] - max(cue["start"], skip)
        if cards.reading(text) > slot + 1e-6:
            raise ValueError(f"{where} : « {text} » demande {cards.reading(text):.1f} s de lecture, "
                             f"son repère dure {slot:.1f} s")


def cut_cues(item: dict, cues: list[dict], lang: str, start: float, offset: float) -> list[dict]:
    """Les sous-titres d'une coupe dans le teaser : les textes du teaser, au moment des repères qu'ils remplacent (pas
    avant le début de la coupe)."""
    first, last = item["cut"]["cues"]
    return [{"start": offset + max(cue["start"], start) - start, "end": offset + cue["end"] - start, "text": text}
            for text, cue in zip(item["captions"][lang], cues[first:last + 1])]


def check_total(seconds: float) -> None:
    if seconds > MAX_SECONDS + 1e-6:
        raise ValueError(f"teaser de {seconds:.1f} s : au plus {MAX_SECONDS:.0f} s")


def piece_command(source: Path, start: float, length: float, target: Path) -> list[str]:
    """Un morceau du teaser en 1920×1080, 30 i/s, yuv420p échelle limitée, sans son."""
    return [tool("ffmpeg"), "-y", "-v", "error", "-i", str(source), "-ss", f"{start:.3f}", "-t", f"{length:.3f}",
            "-vf", f"scale=1920:1080:flags=lanczos:out_range=tv,format=yuv420p,setsar=1,fps={FPS}", "-an",
            "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-color_range", "tv",
            "-r", str(FPS), str(target)]


def plan(teaser: dict, lang: str, film: Path, card_folder: Path) -> tuple[list[tuple[Path, float, float]], list[dict]]:
    """Les morceaux (source, début, durée) et les sous-titres du teaser, tout contrôlé avant ffmpeg."""
    pieces, all_cues, t = [], [], 0.0
    for item in teaser["sequence"]:
        if "card" in item:
            card = card_of(teaser, item["card"])
            pieces.append((card_folder / f"{card['id']}.mp4", 0.0, float(card["seconds"])))
            t += card["seconds"]
            continue
        source, cues = plan_source(film, item["cut"]["capsule"], item["cut"]["plan"], lang)
        check_captions(item, cues, lang)
        start, end = cut_window(cues, *item["cut"]["cues"], probe_duration(source), float(item["cut"].get("from", 0.0)))
        all_cues += cut_cues(item, cues, lang, start, t)
        pieces.append((source, start, end - start))
        t += end - start
    check_total(t)
    return pieces, all_cues


def run(teaser_path: Path, lang: str, out: Path, film: Path = FILM) -> Path:
    teaser = json.loads(teaser_path.read_text(encoding="utf-8"))
    folder = out / lang
    pieces, all_cues = plan(teaser, lang, film, folder / "cards")  # contrôles d'abord : rien n'est rendu pour rien
    cards.run(teaser_path, lang, out)
    work = folder / "pieces"
    work.mkdir(parents=True, exist_ok=True)
    rendered = []
    for n, (source, start, length) in enumerate(pieces):
        target = work / f"{n:02d}-{source.stem}.mp4"
        subprocess.run(piece_command(source, start, length, target), check=True)
        rendered.append(target)
    listing = work / "pieces.txt"
    listing.write_text("".join(concat_line(p) for p in rendered), encoding="utf-8")
    base = folder / f"{teaser['id']}-{lang}"
    vtt, srt = subtitles(all_cues)
    base.with_suffix(".vtt").write_text(vtt, encoding="utf-8")
    base.with_suffix(".srt").write_text(srt, encoding="utf-8")
    # ffmpeg lancé dans le dossier : le filtre d'incrustation lit les sous-titres par un nom simple.
    subprocess.run([tool("ffmpeg"), "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", str(listing.resolve()),
                    "-vf", burn_filter(base.with_suffix(".srt").name), "-an", "-c:v", "libx264", "-preset", "medium",
                    "-crf", "20", "-pix_fmt", "yuv420p", "-color_range", "tv", "-r", str(FPS),
                    "-metadata", f"title={teaser['title'][lang]}", "-movflags", "+faststart",
                    base.with_suffix(".mp4").name], check=True, cwd=folder)
    total = sum(length for _, _, length in pieces)
    print(f"{base.with_suffix('.mp4')} : {total:.1f} s, sans son, {len(pieces)} morceaux, {len(all_cues)} sous-titres")
    return base.with_suffix(".mp4")


def main(argv: list[str]) -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("teaser", type=Path)
    parser.add_argument("--lang", choices=LANGS, default="fr")
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args(argv)
    run(args.teaser, args.lang, args.out or cards.default_out(args.teaser))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
