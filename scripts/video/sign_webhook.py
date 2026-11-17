# /// script
# requires-python = ">=3.11"
# ///
"""Rejoue en local le webhook Yousign d'une signature faite au tournage de C13 (TODO §148, D-VIDEO-SIGN-WEBHOOK, #59).

Yousign ne peut pas joindre un poste de développement. Ce script lit dans l'API du bac à sable l'état réel de la demande,
refuse de continuer si elle n'est pas signée, construit l'événement `signature_request.done` comme Yousign l'envoie, le
signe (HMAC-SHA256 du corps brut, en-tête X-Yousign-Signature-256) avec le secret du connecteur `signature`, puis le
poste au point d'entrée de ce connecteur. La suite est le vrai comportement du produit : téléchargement du PDF signé et
de la preuve chez Yousign, reprise du processus. Outil de tournage : rien n'est ajouté au jar.

    uv run scripts/video/sign_webhook.py --instance <id du connecteur signature> --request <id Myrmacta de la demande>
        [--base-url http://localhost:8080] [--wait 90]

Clés lues dans l'environnement (`.env`, chargé par scripts/video.sh) : YOUSIGN_SANDBOX_API_KEY, YOUSIGN_WEBHOOK_SECRET.
La demande Yousign est celle dont l'`external_id` est l'identifiant Myrmacta de la demande (YousignSignatureAdapter) :
jamais une autre prise ni un autre poste du même bac à sable.
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

SANDBOX = "https://api-sandbox.yousign.app/v3"


def keys() -> tuple[str, str]:
    """La clé d'API et le secret du webhook du bac à sable ; un manque est nommé."""
    missing = [name for name in ("YOUSIGN_SANDBOX_API_KEY", "YOUSIGN_WEBHOOK_SECRET") if not os.environ.get(name)]
    if missing:
        raise SystemExit(f"{', '.join(missing)} absent : à renseigner dans .env (voir .env.local.example, section C13)")
    return os.environ["YOUSIGN_SANDBOX_API_KEY"], os.environ["YOUSIGN_WEBHOOK_SECRET"]


def signature(secret: str, body: bytes) -> str:
    """L'en-tête X-Yousign-Signature-256 : HMAC-SHA256 du corps brut, en hexadécimal, préfixé (YousignWebhookAuthenticator)."""
    return "sha256=" + hmac.new(secret.encode("utf-8"), body, hashlib.sha256).hexdigest()


def event(request: dict, event_id: str, now: int) -> bytes:
    """Le corps de l'événement `signature_request.done`, tel que Yousign le publie, autour de la demande lue chez lui."""
    return json.dumps({
        "event_id": event_id,
        "event_name": "signature_request.done",
        "event_time": str(now),
        "subscription_id": None,
        "subscription_description": "Myrmacta video C13 (local replay)",
        "sandbox": True,
        "data": {"signature_request": request},
    }, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def ensure_done(request: dict) -> None:
    status = request.get("status")
    if status != "done":
        raise SystemExit(f"la demande Yousign {request.get('id')} n'est pas signée (statut {status}) : rien n'est rejoué")


def matching(page: dict, myrmacta_id: str) -> dict:
    """La demande Yousign créée pour la demande Myrmacta `myrmacta_id` (son `external_id`)."""
    for request in page.get("data", []):
        if request.get("external_id") == myrmacta_id:
            return request
    raise SystemExit(f"aucune demande Yousign pour la demande Myrmacta {myrmacta_id} (external_id)")


def refused(status: int, body: bytes) -> None:
    hint = (" : le connecteur de la scène garde les clés de son installation ; après un changement de "
            "YOUSIGN_WEBHOOK_SECRET dans .env, scripts/video.sh reset puis start") if status in (401, 403) else ""
    raise SystemExit(f"le connecteur a répondu {status}{hint} : {body[:300]!r}")


def callback_url(base_url: str, instance: str) -> str:
    return f"{base_url.rstrip('/')}/api/callbacks/{instance}"


def yousign(api_key: str, path: str) -> dict:
    request = urllib.request.Request(SANDBOX + path, headers={"Authorization": f"Bearer {api_key}",
                                                               "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read())


def done_request(api_key: str, myrmacta_id: str, wait: float) -> dict:
    """La demande lue chez Yousign, attendue jusqu'à `wait` secondes : la signature y passe `done` peu après le clic."""
    deadline = time.monotonic() + wait
    while True:
        try:
            rid = matching(yousign(api_key, f"/signature_requests?external_id={myrmacta_id}"), myrmacta_id)["id"]
            found = yousign(api_key, f"/signature_requests/{rid}")
            ensure_done(found)
            return found
        except SystemExit:
            if time.monotonic() >= deadline:
                raise
            time.sleep(3)


def post(url: str, body: bytes, header: str) -> int:
    request = urllib.request.Request(url, data=body, method="POST", headers={
        "Content-Type": "application/json", "X-Yousign-Signature-256": header})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status
    except urllib.error.HTTPError as error:
        refused(error.code, error.read())
        raise


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--instance", required=True, help="identifiant du connecteur signature (Yousign)")
    parser.add_argument("--request", required=True, help="identifiant Myrmacta de la demande de signature")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--wait", type=float, default=90, help="attente maximale de la signature chez Yousign (s)")
    args = parser.parse_args(argv)
    api_key, secret = keys()
    request = done_request(api_key, args.request, args.wait)
    body = event(request, str(uuid.uuid4()), int(time.time()))
    status = post(callback_url(args.base_url, args.instance), body, signature(secret, body))
    print(f"webhook rejoué : signature_request.done {request['id']} -> {status}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
