# §92 — Assignation des tâches — « M'assigner » et annuaire local des utilisateurs

> **Statut :** clos · point de premier niveau · ancien `TODO.md` §92 · [registre](README.md)

[x] livré (branche `feat/assignation-taches`). Avant : la seule façon d'assigner une tâche était la réassignation TA/WA en
saisissant le **sujet OIDC** (UUID) à la main, et une tâche sans assigné n'apparaissait jamais dans **Tâches**.

**D-ASSIGN-DIR** (tranché par l'utilisateur, 2026-10-13) — annuaire **local** (`known_user`, par tenant), alimenté à chaque
connexion OIDC (`KnownUserRecordingSuccessHandler` → `RecordUserLoginUseCase`) ; écarté : API admin Keycloak (secret
supplémentaire, couplage à un IdP). Conséquences assumées : un utilisateur n'apparaît qu'après sa première connexion ; les rôles
stockés sont ceux du dernier jeton (l'IdP reste la référence, seule l'ACL des types est relue en direct). Un échec
d'enregistrement ne bloque jamais la connexion. `GET /api/workflow-links/{id}/assignable-users` (TA/WA) ne renvoie que les
utilisateurs connus **pouvant modifier** le dossier ; la garde anti-faute de frappe de `reassign` accepte désormais un tel
utilisateur en plus des participants/observateurs.

**D-ASSIGN-CLAIM** (tranché par l'utilisateur, 2026-10-13) — `POST /api/workflow-links/{id}/tasks/{taskId}/claim` : tout
utilisateur avec l'ACL **EDIT** du dossier (ou TA/WA) prend une tâche **sans assigné** (cohérent avec D-H2 : EDIT suffit pour
terminer une tâche libre). Tâche déjà prise par un autre → **409** (`TaskService.claim`, atomique, tranche aussi la course
entre deux prises) ; reprendre la sienne est sans effet. Pour que l'éditeur voie la tâche, `GET .../tasks` est ouvert aux
éditeurs du dossier ; diagramme et historique restent réservés aux participants.

**Limite connue :** filtrer l'annuaire par droit EDIT fait une lecture d'ACL par utilisateur connu (acceptable pour V1).

**Impacte :** `docs/guides/GUIDE-UTILISATEUR.md` §3 et §6, `openapi/openapi.yaml`, changeset `035-known-user.yaml`. Aucune variable
d'environnement nouvelle.
