---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

- Un `TENANT_ADMIN` ne peut pas définir la **liste des `FieldDefinition`** (le schéma des champs
  dynamiques) d'un `DossierType` depuis l'écran Admin → Types (`/admin/dossier-types`,
  `DossierTypesAdminView.vue`). Le formulaire de création appelle
  `createDossierType({ …, fieldDefinitions: [] })` en dur ; ni la création ni la modification d'un
  type ne touchent `fieldDefinitions`. `allowUnknownFields` (déjà exposé par l'API) n'a lui non
  plus aucun champ de saisie sur cet écran.
- `GUIDE-UTILISATEUR.md` §1 documente déjà cette limite (« ce formulaire ne permet pas de définir
  les champs dynamiques du type (…) s'adresser à l'équipe technique »). L'unique voie opérationnelle
  aujourd'hui pour peupler `fieldDefinitions` est l'outil MCP interne `define_dossier_type` ou une
  intervention SQL manuelle par l'équipe technique.
- Le backend est déjà prêt (aucune évolution de domaine/port/persistance nécessaire) :
  `POST`/`PATCH /api/dossier-types/{code}` acceptent déjà `fieldDefinitions` en entrée. C'est un
  **écran manquant**, pas un trou d'architecture.
- Affecté : tout `TENANT_ADMIN` qui configure un nouveau type de dossier ou fait évoluer un type
  existant — contournement actuel = ticket à l'équipe technique ou usage du MCP par un opérateur
  technique.

Référence : [analysis doc/17-schema-champs-dynamiques.md](../../../analysis%20doc/17-schema-champs-dynamiques.md).

## Goals & Objectives
**What do we want to achieve?**

Primaire :

- Éditeur de la liste `fieldDefinitions` d'un `DossierType` sur l'écran Admin → Types, en
  **création** et en **modification** : ajout / suppression / réordonnancement de champs, chaque
  champ portant `name`, `label`, `type` (`STRING`/`NUMBER`/`BOOLEAN`/`DATE`/`SELECT`/`FILE`),
  `required`, `defaultValue`, `options` (SELECT uniquement), `group`, `sensitive`.
- Bascule `allowUnknownFields` ajoutée à l'écran (**D-ALLOW-UNKNOWN-SCOPE = inclus**, décidé
  2026-08-28).
- **D-UI-SCHEMA-COMPAT = bloquer par défaut** (décidé 2026-08-28) : `PATCH
  /api/dossier-types/{code}` invoque désormais `CheckDossierTypeSchemaCompatibilityUseCase` avant
  d'appliquer un nouveau schéma et **bloque (409)** si des dossiers existants du type
  deviendraient incohérents avec le nouveau schéma — aligne enfin le comportement REST sur celui
  déjà appliqué par le MCP `define_dossier_type` (qui bloque déjà, sans option de passage en
  force). `DossierTypeSchemaIncompatibleException` mappée dans `DossierExceptionHandler` (409, sur
  le modèle de `DossierConflictException`). L'écran affiche l'erreur retournée par l'API (liste des
  dossiers concernés, cf. `CheckDossierTypeSchemaCompatibilityUseCase.Result.sampleReferences`) —
  pas de "confirmer quand même", puisque le blocage est désormais strict côté serveur.
- **D-NAME-UNIQUE = garde-fou domaine** (décidé 2026-08-28) : ajout d'une contrainte d'unicité de
  `name` au sein de `fieldDefinitions`, portée par le domaine (`DossierType` ou
  `DossierFieldValidator`), pas seulement par le formulaire — protège aussi la création via
  MCP/API directe, cohérent avec le pattern défense-en-profondeur du projet (ex. validation de
  format sur `DossierType.code`, TODO §38).

Secondaire :

- Mettre à jour `GUIDE-UTILISATEUR.md` §1 (retirer le paragraphe « Limite actuelle de l'écran »)
  une fois la feature livrée, conformément à la convention `flowforge-conventions` §6.

Non-goals (explicitement hors périmètre V1, cf. analyse §2) :

- Migration des valeurs existantes lors d'un changement de type de champ (`STRING`→`NUMBER`…) —
  le check de compatibilité *détecte* l'incompatibilité, il ne la résout pas.
- Renommage d'un champ avec réaffectation transparente des valeurs déjà stockées.
- Suppression d'un champ avec purge des valeurs déjà stockées dans les dossiers existants.
- Versionnement de schéma / historique des évolutions de `fieldDefinitions`.
- Passage en force d'une modification bloquée par le check de compatibilité (conséquence de
  D-UI-SCHEMA-COMPAT = bloquer, pas d'échappatoire en V1).
- Édition des **valeurs** de champs sur un dossier — déjà livré (`edition-champs-dossier`, PR #53).

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que `TENANT_ADMIN`, je veux définir la liste des champs dynamiques à la **création**
  d'un `DossierType`, pour ne plus dépendre de l'équipe technique ou du MCP.
- En tant que `TENANT_ADMIN`, je veux **modifier** la liste des champs d'un type existant (ajouter,
  retirer, réordonner, changer `required`/`options`/`sensitive`/`group`), avec la garantie que je
  ne peux pas casser silencieusement des dossiers déjà créés avec ce type.
- En tant que `TENANT_ADMIN`, si ma modification de schéma rend des dossiers existants
  incompatibles, je veux un message d'erreur clair (nombre de dossiers concernés + exemples de
  référence) au lieu d'un échec générique ou d'une incohérence silencieuse.
- En tant que `TENANT_ADMIN`, je veux activer/désactiver `allowUnknownFields` pour un type, ce que
  l'écran ne permet pas aujourd'hui malgré l'exposition API complète.

Cas limites :

- Deux champs avec le même `name` dans le formulaire (même type, en cours d'édition) → bloqué côté
  formulaire ET côté domaine (D-NAME-UNIQUE).
- Modification d'un type **sans aucun dossier existant** → pas de check de compatibilité
  nécessaire (rien à casser), sauvegarde directe.
- Type `SELECT` sans aucune `option` renseignée → rejeté côté formulaire avant tout appel réseau
  (reprend la règle déjà implicite dans `DynamicFields.vue`, qui n'affiche aucune option pour un
  `SELECT` vide).
- `defaultValue` incohérente avec `type` (ex. texte libre pour un `NUMBER`) → validation formulaire
  immédiate, même famille de règle que `DossierFieldValidator.matchesType` côté serveur.

## Success Criteria
**How will we know when we're done?**

- Un `TENANT_ADMIN` peut créer un `DossierType` avec un schéma `fieldDefinitions` complet et
  `allowUnknownFields` défini, sans quitter l'écran Admin → Types.
- Un `TENANT_ADMIN` peut modifier le schéma d'un type existant sans dossiers → sauvegarde directe.
- Un `TENANT_ADMIN` qui tente une modification de schéma incompatible avec des dossiers existants
  reçoit un refus (409) avec un message exploitable (nombre + exemples de dossiers concernés),
  affiché dans l'écran — la modification n'est **pas** appliquée.
- `name` dupliqué dans `fieldDefinitions` rejeté à la fois par le formulaire et par un appel
  direct à l'API (`POST`/`PATCH /api/dossier-types`) contournant l'UI.
- `./mvnw verify` vert (tests API + domaine, dont le nouveau garde-fou d'unicité et le mapping 409)
  et Vitest + Playwright verts (nouveau parcours E2E création + modification bloquée).
- `GUIDE-UTILISATEUR.md` §1 mis à jour, plus de mention de la limite actuelle.

## Constraints & Assumptions
**What limitations do we need to work within?**

- Convention de remplacement intégral déjà établie côté MCP (`define_dossier_type`) :
  `fieldDefinitions` est remplacé en bloc à chaque sauvegarde, pas de diff/patch unitaire par
  champ. L'écran suit la même règle (charge la liste, édite en mémoire, sauvegarde la liste
  complète via `PATCH`).
- Aucun rôle nouveau : `POST`/`PATCH /api/dossier-types` exigent déjà `TENANT_ADMIN`
  (`@PreAuthorize`), cohérent avec l'entrée de menu Admin → Types déjà restreinte.
- Pas de nouvelle route SPA — extension de l'écran `DossierTypesAdminView.vue` existant.
- Style front existant à respecter : éléments HTML natifs + scoped CSS + `data-testid` (pas
  PrimeVue isolé sur ce sous-formulaire) — cohérent avec `DossierTypesAdminView.vue`,
  `DynamicFields.vue`, `DossierFieldsEditor.vue`, malgré [11-frontend.md](../../../analysis%20doc/11-frontend.md)
  qui retient PrimeVue comme librairie cible globale. Confirmation du layout exact déferrée à la
  phase design (cf. Questions & Open Items).
- Backend domaine/port/persistance déjà en place — le travail backend de cette feature se limite à
  brancher `CheckDossierTypeSchemaCompatibilityUseCase` sur `UpdateDossierTypeUseCaseImpl`, mapper
  l'exception en 409, et ajouter la contrainte d'unicité de `name`.

## Questions & Open Items
**What do we still need to clarify?**

Décisions actées 2026-08-28 (`AskUserQuestion`, TODO §47) :

- **D-UI-SCHEMA-COMPAT = bloquer par défaut** (option recommandée retenue).
- **D-NAME-UNIQUE = garde-fou domaine** (option recommandée retenue).
- **D-ALLOW-UNKNOWN-SCOPE = inclus dans le périmètre** (option recommandée retenue).

Restant à trancher en phase design (pas bloquant pour clore les requirements) :

- **Alignement maquette** — [11-frontend.md](../../../analysis%20doc/11-frontend.md) impose
  `FlowForge - Maquettes UI.html` comme référence UX ; aucun écran de ce fichier ne couvre
  aujourd'hui l'édition du schéma de champs. À vérifier en phase design : suivre le pattern visuel
  de `DossierTypesAdminView.vue` (HTML natif + scoped CSS, cf. Constraints) faute de maquette
  dédiée.
- **Forme exacte de l'endpoint de check** — D-UI-SCHEMA-COMPAT retient un blocage strict
  directement dans `UpdateDossierTypeUseCaseImpl.update(...)` (pas de nouvel endpoint de préflight
  séparé, puisqu'il n'y a plus de mode "avertir puis confirmer" à servir). À confirmer en design
  que `CreateDossierTypeUseCaseImpl` n'a pas besoin du même branchement (un type en création n'a
  par construction aucun dossier existant → check non nécessaire, mais à vérifier explicitement
  contre le code).
