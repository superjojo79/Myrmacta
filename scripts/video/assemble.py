# /// script
# requires-python = ">=3.11"
# ///
"""Montage d'une capsule (TODO §128, D-VIDEO-PIPELINE, issue #56).

Pour chaque chapitre : la vidéo du tournage (<nn>-<id>.webm), dont on retire les attentes et accélère les passages notés
dans <nn>-<id>.spans.json, calée sur sa voix (voice/<id>.m4a) : le plus court des deux est prolongé (dernière image
tenue, ou silence). Puis les chapitres bout à bout en un MP4 (H.264, AAC), avec ses chapitres et ses sous-titres (piste
mov_text et fichiers .vtt et .srt à côté).

    uv run scripts/video/assemble.py scripts/video/capsules/l1-mailroom.json --lang fr --out frontend/capture/out/film
    → <out>/<lang>/<capsule>-<lang>.mp4, .vtt, .srt
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

FPS = 30
SIZE = "1920:1080"


def tool(name: str) -> str:
    found = shutil.which(name)
    if not found:
        raise SystemExit(f"{name} introuvable dans le PATH")
    return found


def probe_duration(path: Path) -> float:
    out = subprocess.run([tool("ffprobe"), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         check=True, capture_output=True, text=True).stdout.strip()
    return float(out)


def segments(spans: list[dict], length: float) -> list[tuple[float, float, float]]:
    """Les morceaux gardés de la vidéo : (début, fin, vitesse). Une coupe disparaît ; un passage rapide est accéléré."""
    result, t = [], 0.0
    for span in sorted(spans, key=lambda s: s["start"]):
        start, end = max(span["start"], t), min(span["end"], length)
        if start > t:
            result.append((t, start, 1.0))
        if end > start and span["kind"] == "fast":
            result.append((start, end, float(span.get("factor", 1))))
        t = max(t, end)
    if t < length:
        result.append((t, length, 1.0))
    return [s for s in result if s[1] - s[0] > 0.05]


def screen_length(parts: list[tuple[float, float, float]]) -> float:
    return sum((end - start) / speed for start, end, speed in parts)


def video_filter(parts: list[tuple[float, float, float]], pad: float) -> str:
    chains, labels = [], []
    for i, (start, end, speed) in enumerate(parts):
        chains.append(f"[0:v]trim=start={start:.3f}:end={end:.3f},setpts=(PTS-STARTPTS)/{speed},"
                      f"fps={FPS},scale={SIZE},setsar=1[v{i}]")
        labels.append(f"[v{i}]")
    chains.append("".join(labels) + f"concat=n={len(labels)}:v=1:a=0,tpad=stop_mode=clone:stop_duration={pad:.3f}[v]")
    return ";".join(chains)


def chapter_clip(video: Path, spans: list[dict], voice: Path, target: Path) -> float:
    """Un chapitre monté (MP4) ; rend sa durée."""
    parts = segments(spans, probe_duration(video))
    shown = screen_length(parts)
    spoken = probe_duration(voice)
    length = max(shown, spoken)
    graph = video_filter(parts, length - shown) + f";[1:a]apad=whole_dur={length:.3f}[a]"
    subprocess.run([tool("ffmpeg"), "-y", "-v", "error", "-i", str(video), "-i", str(voice), "-filter_complex", graph,
                    "-map", "[v]", "-map", "[a]", "-t", f"{length:.3f}", "-c:v", "libx264", "-preset", "medium", "-crf", "20",
                    "-pix_fmt", "yuv420p", "-r", str(FPS), "-c:a", "aac", "-b:a", "160k", "-ar", "48000", str(target)],
                   check=True)
    return probe_duration(target)


def timestamp(seconds: float, separator: str) -> str:
    ms = round(seconds * 1000)
    h, ms = divmod(ms, 3_600_000)
    m, ms = divmod(ms, 60_000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d}{separator}{ms:03d}"


def subtitles(cues: list[dict]) -> tuple[str, str]:
    """Les sous-titres en WebVTT et en SRT."""
    vtt = ["WEBVTT", ""]
    srt = []
    for i, cue in enumerate(cues, start=1):
        vtt += [f"{timestamp(cue['start'], '.')} --> {timestamp(cue['end'], '.')}", cue["text"], ""]
        srt += [str(i), f"{timestamp(cue['start'], ',')} --> {timestamp(cue['end'], ',')}", cue["text"], ""]
    return "\n".join(vtt), "\n".join(srt)


def ffmetadata(title: str, chapters: list[tuple[str, float, float]]) -> str:
    lines = [";FFMETADATA1", f"title={title}", ""]
    for name, start, end in chapters:
        lines += ["[CHAPTER]", "TIMEBASE=1/1000", f"START={round(start * 1000)}", f"END={round(end * 1000)}", f"title={name}", ""]
    return "\n".join(lines)


def run(capsule_path: Path, lang: str, out: Path) -> int:
    capsule = json.loads(capsule_path.read_text(encoding="utf-8"))
    folder = out / lang
    voice = json.loads((folder / "voice.json").read_text(encoding="utf-8"))
    work = folder / "clips"
    work.mkdir(parents=True, exist_ok=True)
    clips, marks, all_cues, t = [], [], [], 0.0
    for n, chapter in enumerate(capsule["chapters"]):
        name = f"{n:02d}-{chapter['id']}"
        video = folder / "video" / f"{name}.webm"
        spans = json.loads((folder / "video" / f"{name}.spans.json").read_text(encoding="utf-8"))
        clip = work / f"{name}.mp4"
        length = chapter_clip(video, spans, folder / "voice" / f"{chapter['id']}.m4a", clip)
        clips.append(clip)
        marks.append((chapter["title"][lang], t, t + length))
        all_cues += [{**c, "start": c["start"] + t, "end": c["end"] + t} for c in voice["chapters"][chapter["id"]]["cues"]]
        print(f"{name}: {length:.1f} s")
        t += length

    listing = work / "clips.txt"
    listing.write_text("".join(f"file '{c.resolve().as_posix()}'\n" for c in clips), encoding="utf-8")
    vtt, srt = subtitles(all_cues)
    base = folder / f"{capsule['id']}-{lang}"
    base.with_suffix(".vtt").write_text(vtt, encoding="utf-8")
    base.with_suffix(".srt").write_text(srt, encoding="utf-8")
    metadata = work / "chapters.txt"
    metadata.write_text(ffmetadata(capsule["title"][lang], marks), encoding="utf-8")
    language = {"fr": "fre", "en": "eng"}[lang]
    subprocess.run([tool("ffmpeg"), "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", str(listing),
                    "-i", str(base.with_suffix(".srt")), "-i", str(metadata), "-map", "0:v", "-map", "0:a", "-map", "1:s",
                    "-map_metadata", "2", "-map_chapters", "2", "-c:v", "copy", "-c:a", "copy", "-c:s", "mov_text",
                    "-metadata:s:a:0", f"language={language}", "-metadata:s:s:0", f"language={language}",
                    "-movflags", "+faststart", str(base.with_suffix(".mp4"))], check=True)
    print(f"{base.with_suffix('.mp4')} : {t / 60:.1f} min, {len(marks)} chapitres, {len(all_cues)} sous-titres")
    return 0


def main(argv: list[str]) -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("capsule", type=Path)
    parser.add_argument("--lang", choices=("fr", "en"), default="fr")
    parser.add_argument("--out", type=Path, default=Path("frontend/capture/out/film"))
    args = parser.parse_args(argv)
    return run(args.capsule, args.lang, args.out)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
