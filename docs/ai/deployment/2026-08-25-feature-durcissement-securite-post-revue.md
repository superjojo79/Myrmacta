---
phase: deployment
title: Deployment Strategy
status: draft
created: 2026-08-25
updated: 2026-08-25
feature: durcissement-securite-post-revue
---

# Deployment Strategy — `durcissement-securite-post-revue`

> D-41 / D-42 / D-43 **tranchés**. Notes ops alignées Phase 9.

## Notes ops

- **Local :** `spring.profiles.active=local` (secrets de démo autorisés).
  Actuator `127.0.0.1:8090`. Keycloak `8081`. User JDBC
  `flowforge_tenant_dev` (volume PG **neuf** pour l'init SQL).
- **Prod :** pas de profil `local` ; EPP refuse les défauts ; HMAC M2M
  interdit ; `MANAGEMENT_ADDRESS` / scrape uniquement sur le réseau interne
  si on surcharge le bind localhost.
- **Rollback :** revert git ; Compose : remettre `DB_USERNAME=flowforge`
  si l'init rôle n'a pas été joué.
