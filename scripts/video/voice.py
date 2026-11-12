# /// script
# requires-python = ">=3.11"
# dependencies = ["edge-tts>=7,<8"]
# ///
"""Voix off d'une capsule (TODO §128, D-VIDEO-TTS, issue #56).

Lit chaque phrase de la capsule avec edge-tts (voix neuronales du service en ligne de Microsoft Edge : le texte, public,
part chez Microsoft), mesure sa durée avec ffprobe, puis assemble l'audio de chaque chapitre (phrases séparées par une
courte pause) et écrit ses repères de sous-titres :

    <out>/<lang>/voice/<chapitre>.m4a
    <out>/<lang>/voice.json   {"chapters": {"<id>": {"duration": s, "cues": [{"start", "end", "text"}]}}}

Le tournage lit voice.json pour tenir chaque chapitre au moins le temps de sa voix ; le montage y prend les sous-titres.

    uv run scripts/video/voice.py scripts/video/capsules/l1-mailroom.json --lang fr --out frontend/capture/out/film
"""

from __future__ import annotations

import argparse
import asyncio
import json
import shutil
import subprocess
import sys
from pathlib import Path

PAUSE = 0.45  # silence entre deux phrases (s)
LEAD = 0.6  # silence avant la première phrase d'un chapitre (s)


def tool(name: str) -> str:
    found = shutil.which(name)
    if not found:
        raise SystemExit(f"{name} introuvable dans le PATH (ffmpeg requis pour la voix et le montage)")
    return found


def duration(path: Path) -> float:
    out = subprocess.run([tool("ffprobe"), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         check=True, capture_output=True, text=True).stdout.strip()
    return float(out)


def cues(sentences: list[str], durations: list[float]) -> list[dict]:
    """Un repère par phrase, calé sur l'audio assemblé (pause en tête, puis entre les phrases)."""
    result, t = [], LEAD
    for text, d in zip(sentences, durations, strict=True):
        result.append({"start": round(t, 3), "end": round(t + d, 3), "text": text})
        t += d + PAUSE
    return result


def concat_command(parts: list[Path], target: Path) -> list[str]:
    """ffmpeg : silence de tête, puis chaque phrase suivie d'une pause, en AAC."""
    inputs, filters, labels = [], [], []
    inputs += ["-f", "lavfi", "-t", str(LEAD), "-i", "anullsrc=r=24000:cl=mono"]
    labels.append("[0:a]")
    for i, part in enumerate(parts, start=1):
        inputs += ["-i", str(part)]
        filters.append(f"[{i}:a]aresample=24000,aformat=channel_layouts=mono,apad=pad_dur={PAUSE}[p{i}]")
        labels.append(f"[p{i}]")
    filters.append("".join(labels) + f"concat=n={len(labels)}:v=0:a=1[out]")
    return [tool("ffmpeg"), "-y", "-v", "error", *inputs, "-filter_complex", ";".join(filters), "-map", "[out]",
            "-c:a", "aac", "-b:a", "160k", str(target)]


async def speak(text: str, voice: str, target: Path) -> None:
    import edge_tts

    await edge_tts.Communicate(text, voice).save(str(target))


async def run(capsule_path: Path, lang: str, out: Path) -> int:
    capsule = json.loads(capsule_path.read_text(encoding="utf-8"))
    voice = capsule["voices"][lang]
    folder = out / lang / "voice"
    folder.mkdir(parents=True, exist_ok=True)
    report: dict = {"capsule": capsule["id"], "lang": lang, "voice": voice, "chapters": {}}
    for chapter in capsule["chapters"]:
        sentences = chapter["narration"][lang]
        parts = []
        for i, sentence in enumerate(sentences):
            part = folder / f"{chapter['id']}-{i:02d}.mp3"
            await speak(sentence, voice, part)
            parts.append(part)
        durations = [duration(p) for p in parts]
        target = folder / f"{chapter['id']}.m4a"
        subprocess.run(concat_command(parts, target), check=True)
        report["chapters"][chapter["id"]] = {"duration": round(duration(target), 3), "cues": cues(sentences, durations)}
        for part in parts:
            part.unlink()
        print(f"{chapter['id']}: {report['chapters'][chapter['id']]['duration']:.1f} s, {len(sentences)} phrases")
    (out / lang / "voice.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    total = sum(c["duration"] for c in report["chapters"].values())
    print(f"voix {lang} : {total:.0f} s au total")
    return 0


def main(argv: list[str]) -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("capsule", type=Path)
    parser.add_argument("--lang", choices=("fr", "en"), default="fr")
    parser.add_argument("--out", type=Path, default=Path("frontend/capture/out/film"))
    args = parser.parse_args(argv)
    return asyncio.run(run(args.capsule, args.lang, args.out))


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
