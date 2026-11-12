# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "mcp>=1.9,<2",
#   "openai>=1.80,<3",
#   "pyjwt>=2.9,<3",
# ]
# ///
"""Agent IA de la capsule « Digital Mail Room » (TODO §128, D-VIDEO-AGENT, issue #56).

Un modèle local (Ollama, endpoint compatible OpenAI, `gemma4:12b` par défaut) travaille la file de tri de Myrmacta par le
serveur MCP M2M, avec cinq outils seulement : il lit ses tâches, le fil du courrier, cherche le dossier concerné, rattache
le courrier à blanc puis pour de vrai, et termine la tâche. Chaque étape est écrite sur la sortie standard en une ligne
JSON (`{"kind": ..., ...}`), que le tournage rend dans un panneau façon terminal.

Développement seulement : le jeton M2M est signé localement avec M2M_JWT_SECRET (HMAC, refusé hors profils local/test).

    M2M_JWT_SECRET=... uv run scripts/video/agent.py [--lang fr|en] [--model gemma4:12b] [--max-steps 16]
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
import time
from typing import Any

TOOLS = ("list_my_tasks", "get_mail_thread", "search_dossiers", "attach_mail_to_dossier", "complete_task")
MAX_RESULT_CHARS = 4000
MAX_SAME_CALL = 2

PROMPTS = {
    "fr": (
        "Tu es l'agent de tri du courrier de Myrmacta. Traite chaque tâche « Trier le courrier » de ta file :\n"
        "1. lis le fil du courrier (get_mail_thread, avec l'identifiant du dossier courrier de la tâche) ;\n"
        "2. s'il concerne un dossier existant (une facture citée par son numéro, par exemple), trouve ce dossier : "
        "search_dossiers avec dossierTypeCode=\"FACTURE\" et sans fieldFilters ; le libellé de chaque facture est son "
        "objet (par exemple « Facture 2026-117 ») ;\n"
        "3. rattache le courrier à ce dossier d'abord à blanc (dryRun=true), puis pour de vrai si le contrôle passe ;\n"
        "4. termine la tâche de tri (complete_task).\n"
        "Si tu ne trouves pas de dossier sûr, ne rattache rien et laisse la tâche à un humain. N'invente jamais un "
        "identifiant : prends-les dans les résultats des outils. Réponds en français, en une ou deux phrases à la fin."
    ),
    "en": (
        "You are Myrmacta's mail sorting agent. Work through each \"Sort the mail\" task in your queue:\n"
        "1. read the mail thread (get_mail_thread, with the mail dossier id of the task);\n"
        "2. if it concerns an existing dossier (an invoice quoted by its number, for instance), find that dossier: "
        "search_dossiers with dossierTypeCode=\"FACTURE\" and no fieldFilters; each invoice's label is its subject "
        "(for instance \"Invoice 2026-117\");\n"
        "3. attach the mail to that dossier first as a dry run (dryRun=true), then for real if the check passes;\n"
        "4. complete the sorting task (complete_task).\n"
        "If you find no dossier you are sure of, attach nothing and leave the task to a human. Never make up an id: take "
        "them from the tool results. Answer in English, in one or two sentences at the end."
    ),
}
KICKOFF = {"fr": "Traite ta file de tri.", "en": "Work through your sorting queue."}


def emit(kind: str, **fields: Any) -> None:
    """Une étape de l'agent, en une ligne JSON (lue par le tournage)."""
    print(json.dumps({"kind": kind, "t": round(time.time(), 3), **fields}, ensure_ascii=False), flush=True)


def m2m_token(secret: str, subject: str, tenant: str, ttl_seconds: int = 3600) -> str:
    """Jeton M2M de développement : sujet de l'agent, rôle API_SERVICE, tenant de l'instance (filtre multi-tenant)."""
    import jwt

    now = int(time.time())
    return jwt.encode({"sub": subject, "roles": ["API_SERVICE"], "tenant_id": tenant, "iat": now, "exp": now + ttl_seconds},
                      secret, algorithm="HS256")


def openai_tool(tool: Any) -> dict[str, Any]:
    """Un outil MCP au format des outils OpenAI (fonction, description, schéma JSON des paramètres)."""
    schema = dict(tool.inputSchema or {"type": "object", "properties": {}})
    schema.setdefault("type", "object")
    schema.setdefault("properties", {})
    return {"type": "function", "function": {"name": tool.name, "description": tool.description or "", "parameters": schema}}


def result_text(result: Any) -> str:
    """Le texte d'un résultat d'outil MCP, borné (le contexte d'un petit modèle est court)."""
    parts = [getattr(item, "text", "") for item in (result.content or [])]
    text = "\n".join(p for p in parts if p) or json.dumps(getattr(result, "structuredContent", None) or {})
    return text if len(text) <= MAX_RESULT_CHARS else text[:MAX_RESULT_CHARS] + " …"


def parse_arguments(raw: str | None) -> dict[str, Any]:
    if not raw:
        return {}
    try:
        value = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return value if isinstance(value, dict) else {}


async def run(args: argparse.Namespace) -> int:
    from mcp import ClientSession
    from mcp.client.streamable_http import streamablehttp_client
    from openai import OpenAI

    secret = os.environ.get("M2M_JWT_SECRET", "")
    if not secret:
        emit("error", message="M2M_JWT_SECRET is not set")
        return 2
    headers = {"Authorization": f"Bearer {m2m_token(secret, args.subject, args.tenant)}"}
    llm = OpenAI(base_url=args.llm_url, api_key="ollama")
    emit("start", model=args.model, endpoint=args.llm_url, subject=args.subject, local=True)

    async with streamablehttp_client(args.mcp_url, headers=headers) as (read, write, _):
        async with ClientSession(read, write) as session:
            await session.initialize()
            listed = await session.list_tools()
            tools = [openai_tool(t) for t in listed.tools if t.name in TOOLS]
            missing = sorted(set(TOOLS) - {t["function"]["name"] for t in tools})
            if missing:
                emit("error", message=f"MCP tools missing: {missing}")
                return 2
            emit("tools", names=[t["function"]["name"] for t in tools])

            messages: list[dict[str, Any]] = [{"role": "system", "content": PROMPTS[args.lang]},
                                              {"role": "user", "content": KICKOFF[args.lang]}]
            seen: dict[str, int] = {}
            for _ in range(args.max_steps):
                extra = {"reasoning_effort": args.reasoning_effort} if args.reasoning_effort else {}
                reply = llm.chat.completions.create(model=args.model, messages=messages, tools=tools, temperature=0,
                                                    **extra)
                message = reply.choices[0].message
                calls = message.tool_calls or []
                messages.append({"role": "assistant", "content": message.content or "",
                                 "tool_calls": [c.model_dump() for c in calls] or None})
                if message.content:
                    emit("say", text=message.content.strip())
                if not calls:
                    emit("done")
                    return 0
                for call in calls:
                    arguments = parse_arguments(call.function.arguments)
                    emit("call", tool=call.function.name, arguments=arguments)
                    key = call.function.name + json.dumps(arguments, sort_keys=True)
                    seen[key] = seen.get(key, 0) + 1
                    if call.function.name not in TOOLS:
                        text = f"unknown tool {call.function.name}"
                    elif seen[key] > MAX_SAME_CALL:
                        # Un petit modèle peut rejouer le même appel en boucle : on le lui dit au lieu de le refaire.
                        text = "this exact call was already made; change the arguments or move to the next step"
                    else:
                        try:
                            result = await session.call_tool(call.function.name, arguments)
                            text = result_text(result)
                        except Exception as ex:  # l'agent voit l'erreur et peut corriger son appel
                            text = f"error: {ex}"
                    emit("result", tool=call.function.name, text=text)
                    messages.append({"role": "tool", "tool_call_id": call.id, "content": text})
            emit("error", message=f"stopped after {args.max_steps} steps")
            return 1


def main(argv: list[str]) -> int:
    # Windows : la sortie standard n'est pas en UTF-8 par défaut ; le tournage lit des lignes JSON en UTF-8.
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lang", choices=sorted(PROMPTS), default=os.environ.get("APP_DEMO_LOCALE", "fr"))
    parser.add_argument("--model", default="gemma4:12b")
    parser.add_argument("--llm-url", default="http://localhost:11434/v1")
    parser.add_argument("--reasoning-effort", default="none", help="none (défaut, gemma4) ; vide pour ne rien envoyer")
    parser.add_argument("--mcp-url", default="http://localhost:8080/api/m2m/mcp")
    parser.add_argument("--subject", default="agent-ia-tri", help="sujet du JWT : app.demo.video.agent-subject")
    parser.add_argument("--tenant", default=os.environ.get("APP_TENANT_ID", "tenant_dev"), help="app.tenant.id de l'instance")
    parser.add_argument("--max-steps", type=int, default=16)
    return asyncio.run(run(parser.parse_args(argv)))


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
