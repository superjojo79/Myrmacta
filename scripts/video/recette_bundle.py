# /// script
# requires-python = ">=3.11"
# ///
"""Bundle « venu de la recette » de la capsule C12 (TODO §140, D-VIDEO-SHORT, issue #58).

La démo n'a qu'une instance. Le tournage exporte sa configuration, puis ce script la fait avancer comme l'aurait fait une
recette, avant que le film ne l'importe :
  - un groupe d'utilisateurs « ACHATS » ;
  - un champ facultatif « Bon de commande » sur le type FACTURE.
Le manifeste est réécrit (SHA-256 de chaque artefact, sourceLabel « recette »), comme à l'export.

    uv run scripts/video/recette_bundle.py export.zip recette.zip --lang fr
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

TEXTS = {
    "fr": {"group": "Achats", "field": "Bon de commande", "help": "Numéro du bon de commande, s'il existe."},
    "en": {"group": "Purchasing", "field": "Purchase order", "help": "Purchase order number, if any."},
}


def purchase_order_field(lang: str, template: dict) -> dict:
    """Le nouveau champ, sur le modèle d'un champ existant du type (mêmes clés que l'export)."""
    field = {key: None for key in template}
    field.update({"name": "bonCommande", "label": TEXTS[lang]["field"], "type": "STRING", "required": False,
                  "options": [], "fields": [], "group": template.get("group"), "sensitive": False, "searchable": True,
                  "helpText": TEXTS[lang]["help"], "width": "HALF", "unique": False, "managedByWorkflow": False})
    return {key: value for key, value in field.items() if key in template or key in ("name", "label", "type")}


def run(source: Path, target: Path, lang: str) -> int:
    with zipfile.ZipFile(source) as z:
        files = {name: z.read(name) for name in z.namelist()}
    manifest = json.loads(files.pop("manifest.json"))

    facture_path = "dossier-types/FACTURE.json"
    facture = json.loads(files[facture_path])
    if not any(f["name"] == "bonCommande" for f in facture["fieldDefinitions"]):
        facture["fieldDefinitions"].append(purchase_order_field(lang, facture["fieldDefinitions"][0]))
    files[facture_path] = json.dumps(facture, ensure_ascii=False, indent=2).encode("utf-8")

    group_path = "user-groups/ACHATS.json"
    files[group_path] = json.dumps({"code": "ACHATS", "label": TEXTS[lang]["group"], "idpGroup": None},
                                   ensure_ascii=False, indent=2).encode("utf-8")
    if not any(a["path"] == group_path for a in manifest["artifacts"]):
        manifest["artifacts"].append({"key": "ACHATS", "path": group_path, "type": "USER_GROUP"})

    for artifact in manifest["artifacts"]:
        artifact["sha256"] = hashlib.sha256(files[artifact["path"]]).hexdigest()
    manifest["sourceLabel"] = "recette"

    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        for name, body in files.items():
            z.writestr(name, body)
    print(f"{target} : groupe ACHATS, champ bonCommande sur FACTURE")
    return 0


def main(argv: list[str]) -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("--lang", choices=("fr", "en"), default="fr")
    args = parser.parse_args(argv)
    return run(args.source, args.target, args.lang)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
