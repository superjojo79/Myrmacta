---
phase: requirements
status: reviewed
title: Requirements & Problem Understanding
description: Palette métier dans l'éditeur BPMN (LC1) — briques prêtes à l'emploi, alimentées par les API, écrites en XML validé
---

# Requirements & Problem Understanding — `palette-metier-bpmn` (LC1)

> Sous-lot **LC1** de la couche low-code (D0 = C, TODO §70). Analyse d'origine :
> [39-palette-metier-bpmn.md](../../specs/39-palette-metier-bpmn.md). **Phase 1 du 2026-09-29** : 6
> décisions tranchées par l'utilisateur (`AskUserQuestion`), dont une qui **corrige l'analyse d'origine**
> (voir « Constat qui change le périmètre »). **Phase 2 (revue) close le 2026-09-29** : faits vérifiés dans le code, 1 décision
> supplémentaire (D-PALETTE-REFERENCES), voir « Revue Phase 2 ». **Phase 3** : D-PALETTE-DEPLOY-REFERENCES (existence vérifiée au déploiement). Exposition MCP : [46](../../specs/46-exposition-mcp-couche-low-code.md).

## Problem Statement

Concevoir un workflow dans l'éditeur BPMN (`/admin/bpmn-editor`) suppose de connaître des noms techniques :
`delegateExpression`, `formKey`, `type="dmn"`, et **les noms de variables de process** que chaque brique lit
(`documentType`, `connectorInstanceId`, `targetDossierStatus`…). Le panneau de propriétés n'offre que des champs texte
libres ; toute erreur (delegate inconnu, template supprimé, statut inexistant) n'apparaît qu'**au déploiement**. Le
`GUIDE-CONCEPTION-BPMN.md` (33 Ko) est un prérequis. Or D0 = C vise des **intégrateurs et une couche low-code** :
sans palette métier, ni un utilisateur peu technique ni un futur assistant IA (LC3) ne peuvent composer un BPMN valide.

### Constat qui change le périmètre (vérifié dans le code)

Six delegates lisent leurs paramètres **uniquement dans des variables de process** (`DocumentGenerationTask`,
`ConnectorServiceTask`, `NotificationDispatchTask`, `CreateChildDossierDelegate`, `UpdateDossierStatusDelegate`,
`UpdateWorkflowLinkStatusDelegate`). Le XML d'un `serviceTask` n'a donc **aucun endroit** pour écrire « statut =
CLOTURE » ou « template = X » : l'analyse 39 supposait qu'une brique « écrit elle-même le XML attendu » sans
changement serveur, ce qui est faux pour ces six delegates. Décision **D-PALETTE-PARAMS** : ils lisent d'abord des
**attributs `flowable:*` littéraux** de leur activité (relus dans le BPMN déployé, comme `SignatureRequestTask` §72),
sinon la variable actuelle. Le lot n'est donc plus « frontend seul » : **frontend + delegates + parseur + catalogue**.

## Goals & Objectives

**Primaires**
- Une **palette de briques métier** dans l'éditeur : glisser une brique crée l'élément BPMN pré-configuré.
- Un **panneau par brique** : paramètres typés, **listes alimentées par les API existantes** (templates, instances de
  connecteurs, statuts, tables DMN, schémas de questions, types de dossier…), qui écrit le XML.
- Un **catalogue de briques côté serveur**, **source unique** de l'UI, de la validation au déploiement et du MCP
  (`list_bpmn_building_blocks`).
- **Validation dès la conception** (champ obligatoire vide, référence inexistante), non bloquante.
- **Reconnaissance** d'une brique dans un BPMN déjà déployé ou importé (sans marqueur), sinon affichage brut.
- **Mode expert** : les champs bruts restent accessibles (section « Avancé » repliée, pour tous).

**Secondaires**
- Simplifier fortement `GUIDE-CONCEPTION-BPMN.md` et `14-edition-bpmn.md`.
- Base réutilisable par LC2 (conditions), D3b (mapping) et LC5 (parcours), qui ajouteront leurs briques au catalogue.

**Non-objectifs**
- Aucune **nouvelle surface d'expression** serveur : les attributs sont des **littéraux** (jamais évalués en UEL).
- Pas de brique **multi-éléments** (gabarits) : une brique = un élément BPMN (D-PALETTE-GRANULARITY) ; c'est le rôle de LC5.
- Pas de passerelles, événements, sous-processus dans le catalogue : BPMN pur, inchangé.
- Pas d'éditeur de `connectorInput` / `childDossierFields` / `childWorkflowVariables` / `notificationPayload`
  (structures de type map) : ils restent des variables de process (mode expert / amont du diagramme).
- Pas de nouveau rôle, pas de résolution d'options côté serveur **sauf** l'endpoint de références minimal de D-PALETTE-REFERENCES (3 sources), pas de ressource MCP (D-MCP-LC-RESOURCES non tranchée).
- Pas de briques pour les `formKey` `dossier-fields` et `document-generation` (tranche suivante possible).
- Pas de migration automatique des diagrammes existants (reconnaissance seulement).

## User Stories & Use Cases

- **US-1** En tant que **concepteur de workflow**, je veux poser une brique « Changer le statut du dossier » et choisir
  le statut dans une liste, afin de ne pas connaître `updateDossierStatusTask` ni `targetDossierStatus`.
- **US-2** En tant que concepteur, je veux que les listes (templates, connecteurs, tables DMN, schémas de questions,
  statuts du workflow, types de dossier) viennent des données du tenant, afin de ne pas recopier des identifiants.
- **US-3** En tant que concepteur, je veux être averti dès la conception d'un champ obligatoire vide ou d'une
  référence qui n'existe plus, afin de ne pas découvrir l'erreur au déploiement.
- **US-4** En tant que concepteur, je veux qu'un diagramme déjà déployé s'ouvre avec ses tâches reconnues comme
  briques, afin de le modifier sans tout ressaisir ; et que ce qui n'est pas reconnu reste éditable en brut.
- **US-5** En tant qu'**intégrateur**, je veux garder la saisie brute des attributs (section « Avancé »), afin de
  traiter les cas que la palette ne couvre pas.
- **US-6** En tant que **déployeur**, je veux que les attributs de brique soient validés par le serveur au
  déploiement avec les mêmes allowlists, afin qu'un BPMN écrit à la main ou par MCP ne contourne rien.
- **US-7** En tant qu'**agent IA / client MCP**, je veux lire le catalogue de briques (paramètres typés, sources de
  valeurs), afin de composer un BPMN valide puis le vérifier par `deploy_workflow` en `dryRun` (P1, P4).
- **US-8** En tant que **mainteneur**, je veux qu'une brique ne puisse pas produire un XML que le parseur refuse, afin
  que le catalogue et l'allowlist ne divergent jamais (test de conformité).
- **US-9** En tant que **propriétaire d'un process existant**, je veux qu'aucun diagramme déjà déployé ne change de
  comportement, afin que la palette n'introduise aucune régression.

**Cas limites** : référence supprimée après conception (avertissement, pas d'erreur) ; brique dont la source d'options
est indisponible (liste vide + saisie libre + avertissement) ; attribut de brique **et** variable présents à
l'exécution (l'attribut gagne — révisé 2026-09-30, revue I1 ; la variable ne sert que de repli) ; élément importé avec un attribut inconnu (préservé tel quel, comme aujourd'hui) ;
valeur contenant `${…}` (refusée au déploiement) ; brique sans droit de lecture sur une source (liste vide, message).

## Success Criteria

1. Le catalogue contient **11 briques** (voir design) ; chacune produit un XML **accepté** par le parseur serveur.
2. Une brique posée dans l'éditeur puis déployée s'exécute comme le même diagramme écrit à la main (mêmes variables
   consommées) ; les six delegates lisent l'attribut littéral, **la variable gagnant** si elle existe.
3. **Aucune régression** : les BPMN existants (`src/main/resources/processes/*`) se déploient et s'exécutent à
   l'identique ; tests de non-régression des delegates avec variables seules.
4. Le parseur refuse : attribut de brique inconnu pour le delegate, valeur non littérale (`${`, `#{`), forme invalide
   (UUID, énumération, entier), et ne change rien pour les diagrammes sans attribut. Le **déploiement** refuse en plus une référence
   inexistante d'un nouvel attribut (template, connecteur, type de dossier, configuration de workflow, webhook, statut) ; le `dryRun`
   MCP joue **les mêmes** contrôles (parité).
5. Le panneau propose les listes issues des API existantes (5 sources) et de l'endpoint de références minimal (3 sources
   réservées aujourd'hui à `TENANT_ADMIN`) et n'affiche **aucune donnée d'un autre tenant** ; un `WORKFLOW_ADMIN` obtient
   les mêmes listes qu'un `TENANT_ADMIN` sans accès aux DTO d'administration.
6. Avertissements de conception affichés (panneau + marqueur sur l'élément) sans bloquer l'enregistrement d'un brouillon.
7. Un BPMN existant est reconnu sans modification du XML ; export/import sans perte (attributs `flowable:*` préservés).
8. `GET /api/bpmn-building-blocks` et le tool MCP `list_bpmn_building_blocks` servent le **même** catalogue.
9. Playwright : poser une brique, la configurer, enregistrer, recharger et retrouver la brique.
10. Diagnostics de **compagnons** (D-PALETTE-GRANULARITY) : « Demander une signature » sans attente `signatureCompleted` en aval, et
    « Tâche externe » sans `formKey` valide, sont signalées (non bloquant).
11. `./mvnw verify` (ArchUnit, Jacoco), Vitest et Playwright verts ; `GUIDE-CONCEPTION-BPMN.md` mis à jour.

## Constraints & Assumptions

- **Décisions actées (Phase 1)** : D-PALETTE-MECHANISM = provider maison étendu + entrées de palette (les *element
  templates* bpmn.io ciblent le moddle Camunda, pas Flowable) ; D-PALETTE-CATALOG-SOURCE = catalogue **serveur** servi
  par API (source unique UI/MCP, P5) ; D-PALETTE-EXPERT = champs bruts **toujours visibles**, section « Avancé »
  repliée ; D-PALETTE-SCOPE = **11 briques** ; D-PALETTE-PARAMS = attributs littéraux + repli variable ;
  D-PALETTE-PRECEDENCE = **l'attribut gagne** (révisé 2026-09-30 : à l'origine « la variable gagne ») ; D-PALETTE-GRANULARITY = **un élément** par
  brique ; D-PALETTE-RECOGNITION = par le **motif XML**, sans marqueur ; D-PALETTE-VALIDATION = **avertissements non
  bloquants**, le serveur reste l'arbitre ; D-PALETTE-VALUE-SOURCE = le catalogue **nomme** la source, la SPA appelle
  les API existantes ; D-PALETTE-MCP = tool de lecture dans le **même lot** (P1), pas de ressource MCP.
- **Contraintes techniques** : conventions du dépôt (hexagonal, ArchUnit, TDD) ; parseur et scanner (§57, §69) restent
  les seuls gardiens ; les attributs sont lus depuis le BPMN **déployé**, jamais depuis des variables ; `id` d'activité ≤ 128.
- **Hypothèses** : A1 le namespace `flowable:` reste celui du dépôt ; A2 les endpoints de lecture existants suffisent
  (aucun agrégat) ; A3 `bpmn-js` autorise une entrée de palette personnalisée (`PaletteProvider`) et des marqueurs
  visuels sur les éléments ; A4 les six delegates n'ont pas d'autre consommateur de leurs variables.

## Revue Phase 2 (2026-09-29)

**Vérifié dans le code** : (1) six delegates lisent des variables seules (`DocumentGenerationTask`, `ConnectorServiceTask`,
`NotificationDispatchTask`, `CreateChildDossierDelegate`, `UpdateDossierStatusDelegate`, `UpdateWorkflowLinkStatusDelegate`) et
les noms de variables du catalogue sont exacts ; (2) l'allowlist compte 8 delegates dont `signatureRequestTask` ; (3) le
scanner ne refuse que `class` et le langage de script : les attributs `flowable:*` inconnus sont préservés ; (4) les 8
endpoints de lecture existent ; (5) `attach-document` n'a aucun paramètre ; (6) la route de l'éditeur est réservée à
`TENANT_ADMIN`/`WORKFLOW_ADMIN`.

**Écart trouvé et tranché — D-PALETTE-REFERENCES (utilisateur)** : trois sources (`/api/document-templates`,
`/api/connectors` + `/api/connector-definitions`, `/api/notification-webhooks`) sont **`TENANT_ADMIN` seulement**, alors que l'éditeur
est ouvert à `WORKFLOW_ADMIN`. Décision : un **endpoint de références minimal**, en lecture seule, rôles `TENANT_ADMIN` et
`WORKFLOW_ADMIN`, renvoie `{ valeur, libellé }` pour ces trois sources (aucune configuration, aucun secret, aucun contenu de
template). Les cinq autres sources gardent leurs API existantes. Nuance D-PALETTE-VALUE-SOURCE.

**Précisions ajoutées (non bloquantes, sans question produit)** :
- **Variable prioritaire** : une variable de process de même nom (posée au démarrage ou par une tâche en amont) masque
  l'attribut de la brique ; le panneau l'indique (« une variable `x` prime si elle existe ») pour éviter la surprise.
- **Compagnons** (une brique = un élément) : diagnostics non bloquants pour la signature (attente `signatureCompleted`) et la
  tâche externe (`formKey`).
- **Données personnelles** : un attribut littéral peut contenir une adresse e-mail (`notificationTo`) visible de quiconque lit le
  BPMN ; le panneau recommande une variable ou `notificationUserId` pour une personne physique.
- **Identifiants** : `id` d'activité ≤ 128 (limite existante), noms d'attributs `flowable:<nom de la variable>` (cf. design).
- **Compatibilité de version** : le catalogue porte un `version` ; un attribut de brique inconnu d'une version plus ancienne du
  catalogue est préservé (jamais supprimé).

## Questions & Open Items

Aucune question produit ouverte. **Renvoyées à la Phase 3** : (a) noms exacts des attributs par delegate
(proposition dans le design, à confirmer) ; (b) ~~existence des références au déploiement~~ **tranché en Phase 3** : oui, au use case de déploiement (D-PALETTE-DEPLOY-REFERENCES) ; (c) placement du catalogue dans le domaine (`domain/workflow/palette`) ; (d) briques de tranche suivante
(`dossier-fields`, `document-generation`).
