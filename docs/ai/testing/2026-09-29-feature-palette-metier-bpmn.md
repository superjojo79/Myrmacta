---
phase: testing
status: draft
title: Testing Strategy
description: Scénarios de test de la palette métier BPMN (LC1)
---

# Testing Strategy — `palette-metier-bpmn` (LC1)

> Scénarios dérivés des critères de succès et user stories des
> [requirements](../requirements/2026-09-29-feature-palette-metier-bpmn.md) et du
> [design](../design/2026-09-29-feature-palette-metier-bpmn.md). Cases cochées au fil de l'implémentation.

## Test Coverage Goals

- Backend : 100 % des branches du validateur d'attributs et de `DeployedActivityParameters` ; couverture Jacoco au niveau des seuils du dépôt.
- **Conformité catalogue ↔ parseur** : pour **chaque** brique, un XML généré est accepté ; chaque règle de forme a un cas refusé.
- **Non-régression** : les six delegates avec variables seules se comportent comme avant ; tous les `processes/*.bpmn20.xml` se déploient.
- Frontend : Vitest par module (catalogue, reconnaissance, résolveur de sources, diagnostics, providers) ; **Playwright obligatoire** (conventions du dépôt).
- ArchUnit vert : `domain.workflow.palette` sans dépendance framework.

## Unit & Integration Tests — backend

### Catalogue (US-7, US-8)

- [x] **S-CAT-1** Le catalogue contient exactement 11 briques d'`id` uniques, chacune avec libellé, catégorie, élément et marqueur.
- [x] **S-CAT-2** Chaque paramètre a un `attribute` unique par brique, un type, et une `valueSource` connue quand le type est une référence.
- [x] **S-CAT-3** Aucune donnée du tenant dans le catalogue (test structurel : pas de champ de valeur concrète).
- [x] **S-CAT-4** `GET /api/bpmn-building-blocks` : 200 pour `WORKFLOW_ADMIN`/`TENANT_ADMIN`, 403 sinon ; corps = catalogue.
- [x] **S-CAT-5** Golden OpenAPI : un seul chemin ajouté (`/api/bpmn-building-blocks`).
- [x] **S-REF-1** `references/{source}` : 200 pour `TENANT_ADMIN` **et** `WORKFLOW_ADMIN`, 403 sinon ; source inconnue → 404.
- [x] **S-REF-2** Le corps ne contient que `value` et `label` (test structurel : aucun secret, config de connecteur ni contenu de template).
- [x] **S-REF-3** Isolation tenant : les références d'un autre tenant n'apparaissent jamais.
- [x] **S-REF-4** `connectorInstances` ne liste que les instances de rôle SERVICE.
- [x] **S-CAT-6** Tool MCP `list_bpmn_building_blocks` renvoie le même DTO ; présent au catalogue des tools ; lecture seule.

### Paramètres littéraux à l'exécution (US-1, US-9, D-PALETTE-PARAMS / PRECEDENCE)

Pour **chacun des six delegates** (`documentGeneration`, `connectorService`, `notificationDispatch`, `createChildDossier`, `updateDossierStatus`, `updateWorkflowLinkStatus`) :

- [x] **S-RT-1** Variable seule (diagrammes existants) : comportement inchangé.
- [x] **S-RT-2** Attribut littéral seul : utilisé comme valeur.
- [x] **S-RT-3** Attribut **et** variable : l'attribut gagne (révisé, revue I1 : une variable saisie à l'exécution ne surcharge pas ce que le concepteur a fixé) ; variable seule = comportement d'avant.
- [x] **S-RT-4** Ni l'un ni l'autre : même erreur qu'aujourd'hui (« … est requis »).
- [x] **S-RT-5** Un attribut ne provient que du BPMN **déployé** de l'activité courante (jamais d'une autre activité ni d'une variable).
- [x] **S-RT-6** Une valeur d'attribut n'est jamais évaluée en UEL (chaîne `${…}` refusée au déploiement, et non évaluée si présente).
- [x] **S-RT-7** Moteur réel : un process avec briques 1, 2, 6 en attributs s'exécute de bout en bout (document généré, rattaché, statut changé) sans aucune variable posée.

### Parseur au déploiement (US-6)

- [x] **S-PARSE-1** Chaque brique du catalogue, générée en XML, est **acceptée** (conformité, US-8), pour chacun des bindings (attribut, `flowable:field`, suffixe de `formKey`, éléments `flowable:signer`).
- [x] **S-PARSE-9** Longueurs maximales respectées (`notificationBody` 4000, défaut 200) ; au-delà ⇒ refus.
- [x] **S-PARSE-2** Attribut de brique non autorisé pour le delegate de l'activité → `InvalidBpmnException`.
- [x] **S-PARSE-3** Valeur non littérale (`${x}`, `#{x}`) → refusée, message nommant l'activité et l'attribut.
- [x] **S-PARSE-4** Forme invalide : UUID mal formé (`connectorInstanceId`), énumération inconnue (`targetDossierStatus`, `relationType`, canaux), entier hors bornes.
- [x] **S-PARSE-5** Diagrammes sans attribut de brique : aucun changement (tous les `processes/*` se déploient).
- [x] **S-PARSE-6** Attributs inconnus hors catalogue : traitement actuel inchangé (préservés).
- [x] **S-PARSE-7** `allowlist` des delegates inchangée (`AllowedBpmnDelegates`) ; un delegate hors liste reste refusé.
- [x] **S-PARSE-8** `BpmnExpressionSurfaceScanner` : aucune nouvelle surface (test de non-régression des refus §57/§69).

### Existence des références au déploiement (D-PALETTE-DEPLOY-REFERENCES)

- [x] **S-REFCHK-1** Chaque attribut référençant une ressource (template, instance de connecteur SERVICE, type de dossier, configuration de workflow, webhook) : ressource présente ⇒ déploiement accepté.
- [x] **S-REFCHK-2** Ressource absente (ou template sans version active, connecteur non SERVICE, autre tenant) ⇒ `UnknownBlockReferenceException` nommant activité, attribut et valeur ; 404 comme la clé DMN.
- [x] **S-REFCHK-3** `workflowProcessKey` égal à la clé du process en cours de déploiement ⇒ accepté ; `targetWorkflowStatus` vérifié contre la configuration **du process** (stockée au déploiement, soumise en `dryRun`).
- [x] **S-REFCHK-4** Les constructions existantes (`formKey`, `portalRecipient`, signature, clé DMN) gardent leurs contrôles, sans contrôle supplémentaire (non-régression).
- [x] **S-DRY-1** `deploy_workflow` en `dryRun` joue **les mêmes** contrôles que le déploiement (DMN, portail, signature, références) sans déployer ni écrire.
- [x] **S-DRY-2** Le `dryRun` utilise la configuration soumise (statuts) et non la configuration stockée ; aucun effet de bord.

### Sécurité (T-SEC)

- [x] **T-SEC-1** `GET /api/bpmn-building-blocks` sans session → refusé ; aucune information du tenant dans la réponse.
- [x] **T-SEC-2** Tentative d'injection UEL dans chaque attribut littéral → refus au déploiement.
- [x] **T-SEC-3** Un BPMN déployé par MCP (`deploy_workflow`) passe par le même validateur d'attributs (P6).

## Tests — frontend (Vitest)

### Catalogue, reconnaissance, résolveur (US-2, US-4)

- [x] **S-FE-1** `catalogClient` charge une fois, met en cache ; échec → repli sur le panneau brut sans erreur bloquante.
- [x] **S-FE-2** `recognizeBlock` applique les règles **ordonnées** du design (signature, DMN, six delegates, `portalRecipient`, `formKey` `user-question:`) : 11 briques reconnues ; `userTask` `formKey`+`portalRecipient` ⇒ tâche externe ; `formKey` `dossier-fields`/`document-generation` et élément non reconnu ⇒ `null`.
- [x] **S-FE-3** `valueSourceResolver` : chaque source appelle son API (existante, ou `references/{source}` pour les 3 sources d'administration) et normalise `{ value, label }` ; source indisponible ⇒ liste vide + drapeau (saisie libre).
- [x] **S-FE-4** Aucune donnée mise en cache d'un tenant à l'autre (rechargement au changement de session).

### Palette et panneau (US-1, US-5)

- [x] **S-UI-1** La palette expose un groupe « Briques métier » avec 11 entrées ; créer une brique produit l'élément (type, delegate, `name`) attendu.
- [x] **S-UI-2** Le panneau d'une brique affiche ses paramètres typés (texte, énumération, multi-sélection, sélecteur alimenté, signataires) et écrit **l'attribut** correspondant via `modeling.updateModdleProperties`.
- [x] **S-UI-3** Vider un champ retire l'attribut (pas de chaîne vide).
- [x] **S-UI-4** Section « Avancé » repliée par défaut, contenant les entrées brutes actuelles ; les modifier met à jour le même XML.
- [x] **S-UI-5** Une tâche non reconnue reste éditable avec le panneau brut actuel (aucune régression de `flowablePropertiesProvider`).
- [x] **S-UI-7** Diagramme importé **sans** `xmlns:flowable` : écrire un paramètre de brique déclare l'espace de noms ; `saveXML()` contient l'attribut (D-FLOWABLE-NS).
- [x] **S-UI-8** Le groupe « Flowable » existant devient « Avancé », replié par défaut, mêmes entrées ; le groupe « Brique » apparaît juste après « General ».
- [x] **S-UI-9** Les statuts de workflow proposés sont ceux de la configuration **du process ouvert** ; sans configuration ⇒ saisie libre + avertissement.
- [x] **S-UI-6** Import puis export : attributs `flowable:*` et éléments `flowable:signer` préservés (S-VT-3 du lot signature reste vert).

### Diagnostics (US-3)

- [x] **S-DIAG-1** Paramètre obligatoire vide ⇒ avertissement dans le panneau **et** marqueur sur l'élément ; corriger le retire.
- [x] **S-DIAG-2** Référence absente de la source (template, connecteur, statut, table DMN, schéma) ⇒ avertissement « n'existe plus ».
- [x] **S-DIAG-3** Valeur `${…}` saisie ⇒ avertissement « valeur littérale attendue ».
- [x] **S-DIAG-4** Les avertissements ne bloquent ni l'enregistrement d'un brouillon ni l'export.
- [x] **S-DIAG-6** « Demander une signature » sans attente `signatureCompleted` en aval ⇒ avertissement ; avec elle ⇒ aucun.
- [x] **S-DIAG-7** « Tâche externe » sans `formKey` `user-question:<clé>` valide ⇒ avertissement.
- [x] **S-DIAG-8** Le panneau signale qu'une variable de même nom prime, et déconseille une adresse e-mail littérale dans `notificationTo`.
- [x] **S-DIAG-5** Source indisponible : pas de faux avertissement de référence absente.

## End-to-End Tests (Playwright — API simulée, comme la suite existante)

- [x] **S-PW-1** Poser « Changer le statut du dossier », choisir un statut, enregistrer, recharger : la brique est reconnue avec sa valeur.
- [x] **S-PW-2** Poser « Générer un document » ⇒ liste des templates issue de l'API ; référence supprimée ⇒ avertissement visible.
- [x] **S-PW-3** Ouvrir un BPMN existant (déployé) : tâches reconnues en briques, le reste en brut.
- [x] **S-PW-4** Section « Avancé » : modifier un attribut brut, la valeur se reflète dans le panneau de brique.
- [x] **S-PW-5** Catalogue indisponible (404/500) : l'éditeur reste utilisable en mode brut, message discret.

## E2E réel (backend + navigateur, sur le modèle de `SignatureBrowserE2eTest`)

- [x] **S-E2E-1** (au niveau moteur, sans navigateur) Un BPMN à briques (attributs) s'exécute réellement sans variable de paramètre : `BlockAttributesEndToEndIT` (S-RT-7) sur Flowable réel ; le déploiement par l'API est couvert par `WorkflowBpmnDeploymentApiTest`. Un E2E réel navigateur + serveur (sur le modèle de `SignatureBrowserE2eTest`) n'a **pas** été ajouté : les parcours navigateur sont couverts avec API simulée (`E2E-PALETTE-1..6`), et le rendu réel de l'éditeur a été prouvé par ces tests dans Chromium.

## Test Data

- Fixtures : `BuildingBlockCatalog` (11 blocs), un XML de référence par brique, `processes/*.bpmn20.xml` existants (non-régression), catalogues de sources simulés (templates, connecteurs, statuts, tables DMN, schémas).
- Moteur : Flowable réel (H2) pour S-RT-7 ; mocks de ports pour les tests unitaires de delegates.

## Test Reporting & Coverage

`./mvnw verify` (Jacoco, ArchUnit), `npm run test` (Vitest), `npm run test:e2e` (Playwright).

**Chiffres — Phase 5 (2026-09-29, branche `claude/dreamy-davinci-4ddp96`)**

| Suite | Résultat |
|---|---|
| `./mvnw verify -Dskip.frontend=true` | **BUILD SUCCESS** — 2653 tests, 0 échec, 0 erreur, 3 ignorés (ignorés préexistants), ArchUnit et seuils Jacoco inclus, 4 min 03 |
| Vitest (`npx vitest run`) | 99 fichiers, **498 tests** verts (après corrections de la revue adversariale) |
| `vue-tsc --build`, ESLint | sans erreur |
| Playwright (`npx playwright test`) | **105 tests** verts, dont `E2E-PALETTE-1..6` (S-PW-1..5 + signataires) et `E2E-HDR-3` (éditeur BPMN sous CSP A4) |
| OpenAPI dorée | régénérée, diff additif (132 lignes ajoutées, aucune retirée) |

**Correspondance** : S-PW-1..5 = `E2E-PALETTE-1..5` ; `E2E-PALETTE-6` couvre la liste des signataires (S-UI-2, `SIGNERS`) en navigateur réel.

**Revue de sécurité du parseur (T12.2)** — points vérifiés : les attributs sont lus par **URI d'espace de noms** (un alias
de préfixe ne contourne pas la validation) ; `${` et `#{` refusés sur tous les attributs de brique ; un attribut de brique
hors de son délégué est refusé ; à l'exécution la valeur d'un attribut est traitée comme une **chaîne littérale**,
jamais évaluée (y compris pour un process déployé avant ce lot) ; `BlockAttributeRules` est dérivé du catalogue (pas
de liste parallèle). Aucun constat bloquant.

## Manual Testing

- Composer à la main un process de bout en bout (démarrage → génération → statut) uniquement avec des briques, déployer, exécuter.
- Ouvrir 3 diagrammes existants du dépôt et vérifier la reconnaissance.
- Vérifier la lisibilité clavier de la palette et du panneau.

## Performance Testing

Catalogue statique de 11 blocs (taille non mesurée, quelques Ko) ; chargement unique par session d'éditeur ; les listes de références sont chargées à la demande (panneau ouvert) et, à l'import, pour les seules sources utilisées par les briques du diagramme (marqueurs).

## Bug Tracking

Constats consignés dans `docs/ai/implementation/2026-09-29-feature-palette-metier-bpmn.md` (tableau des écarts).
