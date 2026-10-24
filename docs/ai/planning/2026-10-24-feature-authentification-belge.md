---
phase: planning
title: Authentification belge — plan
description: Découpage initial en lots (issue #210), à affiner après les phases 2 et 3
---

# Plan — `authentification-belge` (issue #210)

Plan initial de phase 1. Chaque tâche de code commence par un test rouge (skill `tdd`).

## Lot A — Keycloak, agents (priorité : échéance du 05/11)

- [x] A1 Simulateur : realm `fas-simulateur` (utilisateurs de test, NRN, niveau), client `private_key_jwt` ; branché sur
      le realm de dev.
- [x] A2 Provisionnement d'un realm par tenant : `provision-tenant.sh --realm-per-tenant` génère `<tenant>.realm.json` et
      `<tenant>.keycloak.sh` (client BFF, rôles, profil, fournisseurs `fas` et `itsme`, flux de première connexion, flux
      `idp-only` et admin de secours) ; tests de structure ; `scripts/keycloak-realm-smoke.sh`.
- [x] A3 Annuaire (A6, défaut existant) : `KnownUserRecordingSuccessHandler` vérifie le `tenant_id` avant
      d'enregistrer.
- [x] A3b Recette locale agents : connexion simulée, compte sans `tenant_id` refusé puis validé, rattachement, mode
      `idp-only`, aucun NRN dans l'ID token.
- [ ] A4 Docs : `03-authentification.md`, `02-multi-tenant.md`, `DEPLOIEMENT.md` (realm par tenant, fournisseurs
      belges, conditions FAS et itsme, procédure de migration).

## Lot B — tiers du portail, serveur

- [x] B1 Domaine : `NationalRegisterNumber`, `IdentityAssuranceLevel`, règle de correspondance, identité vérifiée.
- [x] B2 BPMN : `flowable:portalIdentityField`, `PortalTaskDefinition`, validation au déploiement (E1, E2) ; émission
      `NOT_SENT` / `INVALID_IDENTITY_FIELD` (T2).
- [x] B3 Persistance : changeSet `038` (`portal_identity_attempt`, `portal_link_identity`, `portal_link.identity_field`,
      `task_question_answer.identity_verification`), effacement sur soumission, révocation, expiration et purge.
- [x] B4 Port et adaptateur OIDC direct (FAS, itsme) : URL d'autorisation, échange du code en `private_key_jwt`,
      validation de l'ID token, claims configurables (K5) ; clé de l'instance et JWKS (T11).
- [x] B5 Routes `start`, `callback` et `jwks` dans la chaîne portail ; vue réduite avant l'identité (T3b) ; dépôt et
      soumission avec comparaison ou écriture du champ.
- [x] B6 Test « NRN nulle part » (logs, audit, outbox, variables, webhooks).

## Lot C — tiers du portail, page

- [ ] C1 Page portail : écran d'identité, redirection, retour, refus neutre (Vitest).
- [ ] C2 Playwright (mocks).

## Lot D — documentation et clôture

- [ ] D1 `26-portail-externe.md`, `GUIDE-CONCEPTION-BPMN.md`, `GUIDE-UTILISATEUR.md`, registre §103, `identifiants.md`,
      `scripts/check-docs.sh --write-decisions-index`.
- [ ] D2 Revue adversariale (rejeu, lien volé, oracle sur le NRN, comptes créés par des inconnus) ; vérifications
      finales ; ajout de la recette réelle à #192 ; vider `docs/ai/` de cette feature.

## Dépendances

A1 → A2 → A3 → A4. B1 → B2, B3 → B4 → B5 → B6 ; B4 dépend de A1 (le simulateur sert d'émetteur en recette). C après B5.
D en dernier. Le lot A peut être livré seul, par une PR séparée, avant l'échéance.

## Risques

- Claims et niveaux réels de FAS et d'itsme différents de ceux du simulateur : noms rendus configurables, recette réelle
  dans #192.
- Comptes créés dans le realm du tenant par des inconnus : refusés sans `tenant_id` (A2) ; aucun compte pour un tiers
  (T3, client direct).
- Deux clients à enregistrer par fournisseur et par tenant (Keycloak et instance) : à expliquer dans le guide de
  déploiement et aux communes.
- Échéance serrée : le lot A est prioritaire et autonome.
- CI GitHub Actions hors service (#219) : les vérifications locales font foi.
