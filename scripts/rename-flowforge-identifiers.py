#!/usr/bin/env python3
"""Renomme les identifiants encore en flowforge vers myrmacta / Myrmacta.

Recette reproductible pour réappliquer le renommage sur une branche parallèle
après rebase sur main. Le script est idempotent.

Ne modifie pas :
- les URL ``github.com/superjojo79/FlowForge`` (dépôt non renommé, permaliens compris) ;
- ``flowforge.be`` (tiers, cf. TRADEMARKS.md) ;
- les changeSets Liquibase (``src/main/resources/db/changelog/``) ;
- ``CHANGELOG.md``, ``TRADEMARKS.md``, ``docs/RENAMING.md`` ;
- le registre et les revues (``docs/decisions/``, ``docs/analyses/``), sauf quatre
  renvois dont la cible a bougé : classe d'entrée, skill, maquette UI, export realm ;
- les lignes marquées ``flowforge-legacy`` (compatibilité : ancien en-tête, drapeau
  IMAP, catégorie Outlook, clé de langue, variables d'environnement, mot de passe
  refusé, répertoire de base embarquée).

Usage :
  python3 scripts/rename-flowforge-identifiers.py --dry-run
  python3 scripts/rename-flowforge-identifiers.py --apply
  python3 scripts/rename-flowforge-identifiers.py --check
"""

from __future__ import annotations

import argparse
import os
import re
import sys

ROOT_SKIP_DIRS = {
    ".git",
    "target",
    "node_modules",
    "dist",
    "test-results",
    "playwright-report",
    "coverage",
    ".vite",
}
BINARY_EXT = {
    ".png",
    ".jpg",
    ".jpeg",
    ".gif",
    ".webp",
    ".ico",
    ".woff",
    ".woff2",
    ".ttf",
    ".otf",
    ".jar",
    ".zip",
    ".pdf",
    ".pptx",
    ".gz",
    ".wasm",
}

# Fichiers dont le contenu historique n'est pas réécrit (reliquats volontaires).
FULL_SKIP = {
    "CHANGELOG.md",
    "TRADEMARKS.md",
    "docs/RENAMING.md",
    "scripts/rename-flowforge-identifiers.py",
    # L'auteur des changeSets reste flowforge : la référence de checksums doit
    # porter le même auteur, sinon ChangelogChecksumStabilityIT voit un renommage.
    "src/test/resources/db/changelog/checksums-baseline.csv",
}
SKIP_PREFIXES = (
    "src/main/resources/db/changelog/",
    "docs/decisions/",
    "docs/analyses/",
)

# Dans le registre et les revues, seules ces cibles vivantes sont alignées.
TARGETED = (
    ("FlowForgeApplication", "MyrmactaApplication"),
    ("flowforge-conventions", "myrmacta-conventions"),
    ("FlowForge%20-%20Maquettes%20UI.html", "Myrmacta%20-%20Maquettes%20UI.html"),
    ("FlowForge - Maquettes UI.html", "Myrmacta - Maquettes UI.html"),
    ("flowforge-realm.json", "myrmacta-realm.json"),
)

URL_RE = re.compile(r"https://github\.com/superjojo79/FlowForge[^\s)>\"]*")
FLOWFORGE_RE = re.compile(r"flowforge", re.IGNORECASE)
LEGACY_MARK = "flowforge-legacy"


def rel(root: str, path: str) -> str:
    return os.path.relpath(path, root).replace(os.sep, "/")


def iter_files(root: str):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in ROOT_SKIP_DIRS and not d.startswith(".git")]
        for name in filenames:
            path = os.path.join(dirpath, name)
            ext = os.path.splitext(name)[1].lower()
            if ext in BINARY_EXT:
                continue
            yield path


def classify(path: str) -> str:
    """full = remplacement vivant, targeted = renvois seulement, skip = intouché."""
    if path in FULL_SKIP or path.startswith(SKIP_PREFIXES):
        if path.startswith(("docs/decisions/", "docs/analyses/")):
            return "targeted"
        return "skip"
    return "full"


def shield(line: str) -> tuple[str, list[str]]:
    """Retire les URL du dépôt et flowforge.be avant le remplacement."""
    saved: list[str] = []

    def stash(match: re.Match[str]) -> str:
        saved.append(match.group(0))
        return f"\x00{len(saved) - 1}\x00"

    shielded = URL_RE.sub(stash, line)
    shielded = re.sub(r"flowforge\.be", stash, shielded, flags=re.IGNORECASE)
    return shielded, saved


def unshield(line: str, saved: list[str]) -> str:
    for index, value in enumerate(saved):
        line = line.replace(f"\x00{index}\x00", value)
    return line


def replace_tokens(line: str) -> str:
    line = line.replace("FLOWFORGE", "MYRMACTA")
    line = line.replace("FlowForge", "Myrmacta")
    line = line.replace("Flowforge", "Myrmacta")
    line = line.replace("flowforge", "myrmacta")
    return line


def transform(text: str, mode: str) -> str:
    out = []
    for line in text.splitlines(keepends=True):
        ending = ""
        body = line
        if body.endswith("\r\n"):
            ending = "\r\n"
            body = body[:-2]
        elif body.endswith("\n"):
            ending = "\n"
            body = body[:-1]
        if LEGACY_MARK in body:
            out.append(line)
            continue
        shielded, saved = shield(body)
        if mode == "full":
            shielded = replace_tokens(shielded)
        else:
            for old, new in TARGETED:
                shielded = shielded.replace(old, new)
        out.append(unshield(shielded, saved) + ending)
    return "".join(out)


def looks_binary(path: str) -> bool:
    try:
        with open(path, "rb") as handle:
            return b"\x00" in handle.read(8192)
    except OSError:
        return True


def apply(root: str, write: bool) -> int:
    changed = 0
    for path in iter_files(root):
        relative = rel(root, path)
        mode = classify(relative)
        if mode == "skip" or looks_binary(path):
            continue
        with open(path, "r", encoding="utf-8", errors="strict") as handle:
            try:
                original = handle.read()
            except UnicodeDecodeError:
                continue
        updated = transform(original, mode)
        if updated != original:
            changed += 1
            print(f"{mode}: {relative}")
            if write:
                with open(path, "w", encoding="utf-8", newline="") as handle:
                    handle.write(updated)
    print(f"{changed} fichier(s) {'modifié(s)' if write else 'à modifier'}")
    return changed


def check(root: str) -> int:
    """Échoue si un flowforge vivant n'est ni un reliquat connu ni une ligne legacy."""
    unexpected = []
    for path in iter_files(root):
        relative = rel(root, path)
        mode = classify(relative)
        if mode != "full" or looks_binary(path):
            continue
        try:
            with open(path, "r", encoding="utf-8") as handle:
                lines = handle.readlines()
        except UnicodeDecodeError:
            continue
        for number, line in enumerate(lines, start=1):
            if not FLOWFORGE_RE.search(line):
                continue
            if LEGACY_MARK in line or "flowforge.be" in line.lower() or "pre-myrmacta" in line:
                continue
            if "github.com/superjojo79/FlowForge" in line:
                continue
            unexpected.append(f"{relative}:{number}: {line.strip()[:160]}")
    if unexpected:
        print(f"{len(unexpected)} occurrence(s) vivante(s) hors reliquat :", file=sys.stderr)
        for item in unexpected:
            print(item, file=sys.stderr)
        return 1
    print("aucune occurrence vivante hors reliquats déclarés")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--dry-run", action="store_true")
    group.add_argument("--apply", action="store_true")
    group.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    if args.check:
        return check(root)
    apply(root, write=args.apply)
    return 0


if __name__ == "__main__":
    sys.exit(main())
