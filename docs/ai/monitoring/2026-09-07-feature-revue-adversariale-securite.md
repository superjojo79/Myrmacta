---
phase: monitoring
title: Monitoring & Observability
description: Define monitoring strategy, metrics, alerts, and incident response
status: reviewed
created: 2026-09-07
updated: 2026-09-07
feature: revue-adversariale-securite
---

# Monitoring & Observability — `revue-adversariale-securite`

## Signaux à surveiller après déploiement

| Signal | Interprétation |
|---|---|
| 400 `InvalidBpmnException` au deploy/save | Payload H1 (scriptTask / class / expression / delegate hors catalogue) |
| 403 complete / runtime schema | H2/M8 : non-assignee (attendu) ; pic = régression SPA ou manager EDIT qui complétait pour un collègue |
| 401 webhook HMAC | Partenaire pas encore migré sur `timestamp.Idempotency-Key.body` |
| 401 shared-secret replay | Même corps rejoué dans 5 min (M7) |
| 429 webhook | Rate-limit **après** auth ; flood anonyme ne doit plus vider le bucket |

## Logging

Ne pas logger secrets HMAC, tokens, S3, `authHeaderValue`. Les 401/403 restent
sur `SECURITY_AUDIT` existant.

## Rollback

Si les partenaires HMAC ne peuvent pas migrer : revert de `HmacWebhookAuthenticator`
uniquement (contrat filaire), pas de l’allowlist BPMN.
