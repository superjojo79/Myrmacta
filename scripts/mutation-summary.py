#!/usr/bin/env python3
"""Résumé Markdown d'un rapport de mutation (TODO §108) : score, puis mutants non tués par fichier ou classe.

Lit le rapport XML de PIT (backend) ou le rapport JSON de StrykerJS (frontend, schéma mutation-testing-report).
Usage : scripts/mutation-summary.py [target/pit-reports/mutations.xml | frontend/reports/mutation/mutation.json] [--max N]
Écrit sur la sortie standard (le workflow l'ajoute au résumé du job, $GITHUB_STEP_SUMMARY).
"""

import argparse
import collections
import json
import sys
import xml.etree.ElementTree as ET

PIT_KILLED = {"KILLED", "TIMED_OUT", "MEMORY_ERROR"}
STRYKER_KILLED = {"Killed", "Timeout"}
# Comme le score de Stryker : un mutant qui ne compile pas, plante au chargement ou est ignoré ne compte pas.
STRYKER_INVALID = {"CompileError", "RuntimeError", "Ignored"}

Mutant = collections.namedtuple("Mutant", "unit name line status description killed")


def read_pit(path):
    mutants = []
    for m in ET.parse(path).getroot():
        cls = m.findtext("mutatedClass")
        mutants.append(Mutant(cls.split("$")[0], cls.split(".")[-1], m.findtext("lineNumber"), m.get("status"),
                              m.findtext("description"), m.get("status") in PIT_KILLED))
    return "PIT", mutants


def read_stryker(path):
    with open(path, encoding="utf-8") as report:
        files = json.load(report)["files"]
    mutants = []
    for name, content in files.items():
        for m in content["mutants"]:
            if m["status"] in STRYKER_INVALID:
                continue
            description = m["mutatorName"] + (f" → {m['replacement']}" if m.get("replacement") else "")
            mutants.append(Mutant(name, name.split("/")[-1], str(m["location"]["start"]["line"]), m["status"],
                                  description, m["status"] in STRYKER_KILLED))
    return "StrykerJS", mutants


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("report", nargs="?", default="target/pit-reports/mutations.xml")
    parser.add_argument("--max", type=int, default=60, help="mutants non tués listés au plus")
    args = parser.parse_args()

    try:
        tool, mutations = (read_stryker if args.report.endswith(".json") else read_pit)(args.report)
    except (OSError, ET.ParseError, json.JSONDecodeError, KeyError) as error:
        print(f"Rapport de mutation illisible ({args.report}) : {error}")
        return 1

    total = len(mutations)
    killed = sum(1 for m in mutations if m.killed)
    no_coverage = sum(1 for m in mutations if m.status in ("NO_COVERAGE", "NoCoverage"))
    covered = total - no_coverage
    print(f"## Tests de mutation ({tool})\n")
    if total == 0:
        print("Aucun mutant généré.")
        return 0
    print(f"- Mutants : **{total}**, tués : **{killed}** — score **{round(100 * killed / total)} %**")
    if covered:
        print(f"- Force des tests (mutants couverts) : **{round(100 * killed / covered)} %**")
    print(f"- Non couverts : {no_coverage}\n")

    survivors = [m for m in mutations if not m.killed]
    if not survivors:
        return 0
    by_unit = collections.Counter(m.unit for m in survivors)
    print("| Fichier ou classe | Non tués |\n|---|---|")
    for name, count in by_unit.most_common():
        print(f"| `{name}` | {count} |")
    print(f"\n<details><summary>Détail ({min(len(survivors), args.max)} sur {len(survivors)})</summary>\n")
    print("| Fichier ou classe | Ligne | Statut | Mutation |\n|---|---|---|---|")
    for m in survivors[: args.max]:
        description = m.description.replace("|", "\\|").replace("\n", " ")
        print(f"| `{m.name}` | {m.line} | {m.status} | {description} |")
    print("\n</details>")
    return 0


if __name__ == "__main__":
    sys.exit(main())
