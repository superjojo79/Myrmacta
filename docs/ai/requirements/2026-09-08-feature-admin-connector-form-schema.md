---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding

Feature : `admin-connector-form-schema` (lot **A2**, [FILE-ATTENTE.md](../../../FILE-ATTENTE.md)).
Agent cloud autonome — décisions ci-dessous actées sans `AskUserQuestion` (contrainte session) ;
chaque D-* est ancrée dans le code réel et l’analyse, pas inventée.

## Problem Statement
**What problem are we solving?**

- Un `TENANT_ADMIN` crée une instance de connecteur via Admin → Connecteurs
  (`/admin/connectors`, `ConnectorsAdminView.vue`) en collant du **JSON brut** dans un
  `<textarea>` (`form.configJson` + `JSON.parse` côté client). Un JSON mal formé bloque la
  création avec une erreur générique. Les champs réellement attendus (`webhookSecret`,
  `processKey`, SELECT `webhookAuthType`, etc.) ne sont pas visibles dans le formulaire.
- `GET /api/connector-definitions` **renvoie déjà** `configSchema` (`List<ConfigField>` :
  `name`, `label`, `type`, `required`, `defaultValue`, `options`, `helpText`) — vérifié dans
  `ConnectorDefinitionResponse` / OpenAPI `ConfigField`. Le frontend n’en lit que `type` et
  `label` (`ConnectorDefinitionItem` dans `frontend/src/api/admin.ts`).
- Cible produit déjà écrite : [06-connecteurs.md](../../../analysis%20doc/06-connecteurs.md)
  « Le schéma de configuration est déclaratif […] **Cible produit :** un formulaire d'admin
  généré dynamiquement à partir de ce schéma. **V1 livré :** textarea JSON ». Ce lot livre
  cette cible, sans toucher au backend.
- Affecté : tout `TENANT_ADMIN` qui configure `http-rest`, `flowable-signal`, `scheduler`,
  `flowable-callback`, `smtp`, `document-storage`. Contournement actuel = coller un exemple
  JSON depuis `GUIDE-UTILISATEUR.md` §4 (lui-même **périmé** : il ne liste que 2 types alors
  que le catalogue en a 6).

## Goals & Objectives
**What do we want to achieve?**

Primaire :

- Formulaire de création généré depuis `configSchema` du type sélectionné, à la place du
  `<textarea>` JSON (**D-REPLACE-TEXTAREA**).
- Tous les `ConfigFieldType` du domaine : `STRING`, `PASSWORD`, `URL`, `INTEGER`, `BOOLEAN`,
  `SELECT` (les builtins n’utilisent aujourd’hui que STRING / PASSWORD / SELECT — INTEGER /
  BOOLEAN / URL doivent quand même être rendus, pour un connecteur custom).
- Changement de type → le formulaire se réinitialise aux `defaultValue` du nouveau schéma
  (**D-SWITCH-TYPE**).
- Schéma vide (`smtp`, `document-storage`) → aucun champ de config, payload `config: {}`
  (**D-EMPTY-SCHEMA**). Pas de textarea de repli dans ce cas (un JSON libre enverrait des
  clés inconnues, déjà rejetées par `assertKnownConfigKeys`).
- Mettre à jour `GUIDE-UTILISATEUR.md` §4 (formulaire champ par champ + catalogue réel).

Secondaire :

- Fallback **uniquement** si le catalogue n’est pas chargé (`definitions.length === 0`) :
  saisie libre du type + textarea JSON, comportement actuel (**D-FALLBACK-NO-DEFS**).
- Afficher `helpText` et `label` du schéma ; champs `required` marqués et `required` HTML.

Non-goals :

- Édition de la `config` d’une instance **déjà créée** (l’écran actuel ne le permet pas ;
  update = toggle `enabled` / delete uniquement — **D-SCOPE-CREATE**).
- Changement backend : `ConnectorCatalog`, validateurs, OpenAPI, MCP, `SecurityConfig`,
  Compose (surface exclusive A2 = FE + guide).
- Réutiliser `DynamicFields.vue` (enum dossier `NUMBER`/`DATE`/`FILE` ≠ `ConfigFieldType`).
- Éditeur JSON structuré pour le champ `mapping` (reste un `STRING` / textarea d’une ligne).

## User Stories & Use Cases

- En tant que `TENANT_ADMIN`, je sélectionne `http-rest` et je vois les champs
  `webhookAuthType` (liste), `webhookSecret` (mot de passe), `processKey`, `dossierTypeCode`,
  `authHeaderValue` (optionnel, mot de passe), `mapping` (optionnel) — pas un blob JSON.
- En tant que `TENANT_ADMIN`, je crée un `smtp` : seul le nom est demandé ; la config part
  vide.
- En tant que `TENANT_ADMIN`, si je change de type après avoir saisi, les valeurs du type
  précédent ne fuient pas vers le nouveau.
- En tant que `TENANT_ADMIN`, un champ requis vide est bloqué par le navigateur avant l’API ;
  un secret trop court est toujours rejeté **par le serveur** (message existant).
- Cas limites : catalogue vide (textarea) ; `helpText` long (HMAC) affiché ; BOOLEAN /
  INTEGER / URL rendus même si aucun builtin ne les utilise aujourd’hui.
- En tant que `TENANT_ADMIN`, après création d’un `http-rest` je vois l’URL webhook
  `POST …/api/webhooks/{id}` dans la liste (copie presse-papiers) ; pour `flowable-callback`,
  l’URL callback `POST …/api/callbacks/{id}` (**D-WEBHOOK-URL** = C, stakeholder 2026-09-08).

## Success Criteria

- [x] Plus de `<textarea>` JSON sur le chemin nominal (catalogue chargé, y compris schéma vide).
- [x] `POST /api/connectors` envoie un objet `config` typé (INTEGER = number, BOOLEAN = boolean,
      optionnels vides omis) construit depuis le formulaire, pas `JSON.parse` d’un blob.
- [x] Vitest : composant de formulaire + vue d’admin (création, switch de type, smtp vide,
      fallback catalogue vide).
- [x] Playwright : au moins un scénario de création `http-rest` (SELECT + PASSWORD + STRING)
      et un scénario schéma vide (`smtp`) ; mocks `GET /api/connector-definitions` portent
      `configSchema` ; après création, URL webhook / callback visibles (D-WEBHOOK-URL).
- [x] `GUIDE-UTILISATEUR.md` §4 décrit le formulaire et le catalogue réel (6 types).
- [x] Zéro fichier Java / `ConnectorCatalog` / MCP / Compose / `SecurityConfig` modifié.

## Constraints & Assumptions

- Surface exclusive A2 : `ConnectorsAdminView.vue`, `frontend/src/api/admin.ts`, e2e
  connecteurs, `GUIDE-UTILISATEUR.md` § connecteurs. Nouveaux fichiers **frontend** autorisés
  (A1/A3 n’écrivent pas dans `frontend/**`).
- Rôle inchangé : `TENANT_ADMIN` (`routes.ts` déjà).
- Validation métier (longueur `webhookSecret`, cron, JSONPath `mapping`) **reste serveur**.
- `assertKnownConfigKeys` rejette toute clé hors schéma — le formulaire ne doit envoyer que
  des clés du schéma.
- Agent cloud : pas d’`AskUserQuestion` ; D-* ci-dessous = recommandations code + analyse.

## Décisions (Phase 1–2)

| ID | Choix | Alternatives écartées | Preuve |
|---|---|---|---|
| **D-REPLACE-TEXTAREA** | Formulaire généré remplace le textarea dès que le type est dans le catalogue | Garder le JSON en plus (« expert ») | Cible `06-connecteurs.md` ; JSON expert = clés hors schéma → 400 |
| **D-EMPTY-SCHEMA** | Schéma `[]` → message + `config: {}` | Textarea `{}` | `SmtpConnector` / `DocumentStorageConnector` ont `List.of()` ; `assertKnownConfigKeys` refuse toute clé |
| **D-FALLBACK-NO-DEFS** | Catalogue vide → type libre + textarea (comportement actuel) | Formulaire impossible sans schéma | Défense si l’API définitions échoue |
| **D-SCOPE-CREATE** | Création seulement | Ajouter l’édition de config | Hors brief ; update actuel = enabled/delete |
| **D-NO-DYNAMICFIELDS** | Nouveau `ConnectorConfigForm` | Réutiliser `DynamicFields.vue` | Enums incompatibles (`PASSWORD`/`URL`/`INTEGER` vs `NUMBER`/`DATE`/`FILE`) |
| **D-COERCE** | INTEGER→`number`, BOOLEAN→`boolean`, optionnel blank **omis** | Tout stringifier | Aligné JSON backend / `validate()` |
| **D-PASSWORD** | `input type=password` `autocomplete=new-password` | Texte clair | Secrets connecteur ; jamais réaffichés en liste (`secretConfigured`) |
| **D-CLIENT-REQUIRED** | `required` HTML + `helpText` ; pas de duplication des règles serveur | Recoder min-20 / cron en FE | Source de vérité = `ConnectorDefinition.validate` |
| **D-SWITCH-TYPE** | Changement de type → reset aux defaults du nouveau schéma | Fusionner les clés homonymes | Évite de poster `webhookSecret` sur un `scheduler` |
| **D-NO-BACKEND** | Zéro Java | Étendre le schéma | FILE-ATTENTE A2 : l’API expose déjà `configSchema` |
| **D-WEBHOOK-URL** | **C** — afficher dans la liste l’URL webhook (`http-rest`) **et** l’URL callback (`flowable-callback`), avec copie | A = ne pas afficher (limite GUIDE) ; B = webhook seulement | Stakeholder 2026-09-08 ; l’id est déjà dans `GET /api/connectors` |

## Questions & Open Items

Aucun. Les D-* ferment le cadrage, y compris **D-WEBHOOK-URL = C** (rejeu Phase 1–3, 2026-09-08).
Hors scope volontaire : édition d’instance, éditeur JSON pour `mapping`.
