# Internationalisation de l'UI (D7)

> **Statut : première tranche livrée (TODO §90, 2026-10-01) — décisions D-I18N-* dans `docs/roadmap-goal/DECISIONS.md` ; libellés métier non traduits.** Lot **D7**
> `internationalisation-ui` de la Vague D (`ROADMAP.md` Catégorie 7). Priorité selon le marché
> visé (D0 = C, [31](31-positionnement-produit.md)). À affiner en `dev-lifecycle` Phase 1.

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Bibliothèque i18n | Aucune (`vue-i18n` absent) | `frontend/package.json` |
| Textes du SPA | En dur dans les composants, en français (« Enregistrer le brouillon », « Déployer »…), avec quelques libellés anglais résiduels | `frontend/src/views/admin/*.vue` |
| Portail externe | Build Vite séparé `/portal/`, textes également en dur | PR #138, D-PORTAL-UI-BUILD |
| Documents | Templates PDF avec **variantes par langue** et repli | [08-templates-documents.md](08-templates-documents.md) |
| E-mails | Invitations portail, alertes SLA : textes produits côté serveur | `PortalEmails`, `SlaAlertJob` |
| Libellés métier | Libellés de champs, statuts et types saisis par le tenant, dans une seule langue | `FieldDefinition.label`, `statusDefinitions` |

## 2. Objectif

Proposer l'interface (back-office et portail) dans plusieurs langues, avec une langue par défaut
par tenant et un choix par utilisateur.

## 3. Périmètre à poser (Phase 1)

- **Chrome applicatif** (menus, boutons, messages) : extraction des chaînes, `vue-i18n`,
  fichiers de traduction, détection de la langue (profil OIDC, navigateur, réglage tenant).
- **Portail** : prioritaire si des tiers étrangers y répondent.
- **E-mails serveur** : modèles par langue, sur le modèle des variantes de templates.
- **Libellés métier** (champs, statuts, types) : traduisibles par le tenant ou non — c'est le
  point le plus coûteux (schéma de `FieldDefinition` à étendre).
- **Formats** : dates, nombres, montants (lien avec `MONEY` de D1).

## 4. Coutures

Tous les composants Vue (extraction mécanique mais large), portail, messages d'erreur de l'API
(codes stables + libellés côté client ?), e-mails serveur, et `FieldDefinition` si les libellés
métier sont traduisibles.

## 5. Exposition MCP

Si les libellés métier deviennent traduisibles, `define_dossier_type` et
`define_task_question_schema` portent les traductions (principe P1 de
l'[analyse 46](46-exposition-mcp-couche-low-code.md)). Sinon, aucune.

## 6. Décisions à trancher

1. **D-I18N-LANGS** — langues de la première tranche.
2. **D-I18N-SCOPE** — chrome seul, ou aussi libellés métier et e-mails.
3. **D-I18N-DETECT** — source de la langue (profil OIDC, navigateur, réglage tenant).
4. **D-I18N-ERRORS** — messages d'erreur API traduits côté serveur ou côté client.

## 7. Documentation impactée

`GUIDE-UTILISATEUR.md`, `11-frontend.md`, tests Playwright (sélecteurs par texte à revoir).
