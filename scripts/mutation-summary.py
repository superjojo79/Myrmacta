#!/usr/bin/env python3
"""Résumé Markdown d'un rapport PIT (TODO §108) : score, puis mutants non tués par classe.

Usage : scripts/mutation-summary.py [target/pit-reports/mutations.xml] [--max N]
Écrit sur la sortie standard (le workflow l'ajoute au résumé du job, $GITHUB_STEP_SUMMARY).
"""

import argparse
import collections
import sys
import xml.etree.ElementTree as ET

KILLED = {"KILLED", "TIMED_OUT", "MEMORY_ERROR"}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("report", nargs="?", default="target/pit-reports/mutations.xml")
    parser.add_argument("--max", type=int, default=60, help="mutants non tués listés au plus")
    args = parser.parse_args()

    try:
        mutations = list(ET.parse(args.report).getroot())
    except (OSError, ET.ParseError) as error:
        print(f"Rapport PIT illisible ({args.report}) : {error}")
        return 1

    total = len(mutations)
    killed = sum(1 for m in mutations if m.get("status") in KILLED)
    no_coverage = sum(1 for m in mutations if m.get("status") == "NO_COVERAGE")
    covered = total - no_coverage
    print("## Tests de mutation (PIT)\n")
    if total == 0:
        print("Aucun mutant généré.")
        return 0
    print(f"- Mutants : **{total}**, tués : **{killed}** — score **{round(100 * killed / total)} %**")
    if covered:
        print(f"- Force des tests (mutants couverts) : **{round(100 * killed / covered)} %**")
    print(f"- Non couverts : {no_coverage}\n")

    survivors = [m for m in mutations if m.get("status") not in KILLED]
    if not survivors:
        return 0
    by_class = collections.Counter(m.findtext("mutatedClass").split("$")[0] for m in survivors)
    print("| Classe | Non tués |\n|---|---|")
    for name, count in by_class.most_common():
        print(f"| `{name}` | {count} |")
    print(f"\n<details><summary>Détail ({min(len(survivors), args.max)} sur {len(survivors)})</summary>\n")
    print("| Classe | Ligne | Statut | Mutation |\n|---|---|---|---|")
    for m in survivors[: args.max]:
        description = m.findtext("description").replace("|", "\\|")
        print(f"| `{m.findtext('mutatedClass').split('.')[-1]}` | {m.findtext('lineNumber')} "
              f"| {m.get('status')} | {description} |")
    print("\n</details>")
    return 0


if __name__ == "__main__":
    sys.exit(main())
