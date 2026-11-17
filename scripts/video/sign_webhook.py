# /// script
# requires-python = ">=3.11"
# ///
"""Rejoue en local le webhook Yousign d'une signature faite au tournage de C13 (TODO §148, D-VIDEO-SIGN-WEBHOOK, #59).

Yousign ne peut pas joindre un poste de développement. Ce script lit dans l'API du bac à sable l'état réel de la demande,
refuse de continuer si elle n'est pas signée, construit l'événement `signature_request.done` comme Yousign l'envoie, le
signe (HMAC-SHA256 du corps brut, en-tête X-Yousign-Signature-256) avec le secret du connecteur `signature`, puis le
poste au point d'entrée de ce connecteur. La suite est le vrai comportement du produit : téléchargement du PDF signé et
de la preuve chez Yousign, reprise du processus. Outil de tournage : rien n'est ajouté au jar.

    uv run scripts/video/sign_webhook.py --instance <id du connecteur signature> [--request <id Yousign>]
        [--base-url http://localhost:8080] [--wait 90]

Clés lues dans l'environnement (`.env`, chargé par scripts/video.sh) : YOUSIGN_SANDBOX_API_KEY, YOUSIGN_WEBHOOK_SECRET.
Sans --request : la dernière demande signée du bac à sable.
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


def latest_done(page: dict) -> str:
    """L'identifiant de la demande signée la plus récente (Yousign liste les plus récentes d'abord)."""
    done = [r for r in page.get("data", []) if r.get("status") == "done"]
    if not done:
        raise SystemExit("aucune demande signée dans le bac à sable Yousign : signer d'abord, ou passer --request")
    return done[0]["id"]


def callback_url(base_url: str, instance: str) -> str:
    return f"{base_url.rstrip('/')}/api/callbacks/{instance}"


def yousign(api_key: str, path: str) -> dict:
    request = urllib.request.Request(SANDBOX + path, headers={"Authorization": f"Bearer {api_key}",
                                                               "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read())


def done_request(api_key: str, request_id: str | None, wait: float) -> dict:
    """La demande lue chez Yousign, attendue jusqu'à `wait` secondes : la signature y passe `done` peu après le clic."""
    deadline = time.monotonic() + wait
    while True:
        try:
            rid = request_id or latest_done(yousign(api_key, "/signature_requests?status=done&limit=1"))
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
        raise SystemExit(f"le connecteur a répondu {error.code} : {error.read()[:300]!r}") from error


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--instance", required=True, help="identifiant du connecteur signature (Yousign)")
    parser.add_argument("--request", help="identifiant de la demande Yousign (défaut : la dernière signée)")
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
