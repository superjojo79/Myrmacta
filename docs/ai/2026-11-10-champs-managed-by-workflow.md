# Champs de dossier gérés par le workflow (`managedByWorkflow`) — document de travail

Issue : [#9](https://github.com/superjojo79/Myrmacta/issues/9) · Spec du domaine : [docs/specs/04-dossiers.md](../specs/04-dossiers.md), [17-schema-champs-dynamiques.md](../specs/17-schema-champs-dynamiques.md), [32-modele-champs-enrichi.md](../specs/32-modele-champs-enrichi.md), [39-palette-metier-bpmn.md](../specs/39-palette-metier-bpmn.md) · Points du registre : §112 (constat H1) ; nouveau point à ouvrir en conception

## Cadrage

- Problème et utilisateurs :
  - Un identifiant technique rendu par une application (compte IAM, matricule SIRH) ne peut pas vivre en sécurité dans un champ de dossier modifiable par toute personne qui a le droit EDIT (création, `PATCH .../fields`, formulaires `dossier-fields`). Un `DELETE` / `PUT` piloté par ce champ viserait alors un autre objet (constat H1, §112 / #260).
  - Contournement actuel : variables de process + discipline de conception non vérifiée (`exemples/onboarding-collaborateur`).
  - Utilisateurs : intégrateurs BPMN / admins de type de dossier ; agents métier (ne doivent plus pouvoir forger ces champs) ; le runtime workflow (`fieldMappingTask`).
- Périmètre / hors-périmètre :
  - **Dans** : drapeau `managedByWorkflow` sur `FieldDefinition` (champ racine) ; refus d’écriture sur toutes les surfaces hors `fieldMappingTask` ; lecture via `dossier.fields` dans les expressions ; UI lecture seule (détail) / absent des formulaires de saisie ; migration de l’exemple onboarding ; doc guides + specs ; point de registre qui clôt l’arbitrage #9.
  - **Hors** : variables de process « protégées » (option 2 de l’issue) ; nouveau régime de redaction / export lié au drapeau ; `managedByWorkflow` sur sous-champs de `REPEATABLE_GROUP` ; cumul avec `formula`.
- Critères de succès (vérifiables) :
  1. Définir un champ `managedByWorkflow: true` sur un type de dossier est accepté (admin) ; incompatible avec `required`, `formula`, `defaultValue`, et avec un sous-champ de groupe.
  2. Création de dossier, `PATCH /api/dossiers/{id}/fields`, complétion `dossier-fields`, portail, MCP d’écriture de champs, et tout autre appelant hors `fieldMappingTask` : refus si la payload touche un champ géré (y compris valeur égale à l’existante).
  3. `fieldMappingTask` écrit ces champs ; les expressions lisent `dossier.fields.<nom>` (sauf si `sensitive`).
  4. UI : champ absent des formulaires de saisie ; visible en lecture seule sur le détail ; drapeau configurable dans l’éditeur de schéma du type.
  5. L’exemple onboarding utilise des champs `managedByWorkflow` pour les identifiants techniques ; `OnboardingOrchestrationExampleIT` prouve qu’une modification humaine de ces champs est refusée (ou impossible via les surfaces testées) et qu’un abandon / compensation ne peut plus être détourné en forgeant le champ.
  6. `./mvnw verify`, contrôles docs, et si le SPA / contrats FE changent : `npm run lint` + `npm run test:all`.
- Contraintes (stack, tenant, sécurité, MCP, documentation à mettre à jour) :
  - Domaine pur + ports ; persistance JPA seulement ; pas de changeSet Liquibase modifié (nouveau changeSet pour la colonne / JSON du schéma si besoin).
  - `sensitive` reste orthogonal : compatible, mais alors invisible aux expressions (cas H1 = non sensible).
  - `searchable` / `unique` : hypothèse — règles existantes de `FieldDefinition` inchangées.
  - `visibleWhen` : hypothèse — autorisé (affichage conditionnel au détail) ; `requiredWhen` interdit (pas de `required`).
  - Doc : `GUIDE-CONCEPTION-BPMN.md` (remplacer l’avertissement « variables only » / lien #9), `GUIDE-UTILISATEUR.md` (schéma de champs), specs 04 / 17 / 32 / 39, exemple + README onboarding, registre (nouveau §, clôture arbitrage).
- Validé par l'utilisateur le : _(en attente)_

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Nom du drapeau | `managedByWorkflow` / `readOnly` / les deux | `managedByWorkflow` | tranchée → conception |
| `required` | interdit / ignoré à l’écriture humaine / contrôlé au mapping | interdit (comme `formula`) | tranchée |
| Cumul avec `formula` | exclusifs / asymétrique / cumul | exclusifs | tranchée |
| Surfaces d’écriture refusées | tout sauf `fieldMappingTask` / littéral issue / + seed | tout sauf `fieldMappingTask` | tranchée |
| Migrer onboarding | oui / non / hybride | oui | tranchée |
| UI | lecture seule détail / masqué / disabled en saisie | lisible détail, absent saisie | tranchée |
| Compatibilité `sensitive` | orthog. / incompatible / nouveau régime | orthog. | tranchée |
| `defaultValue` | interdit / appliqué à la création / métadonnée morte | interdit | tranchée |
| Sous-champ de groupe | non V1 / oui / groupe entier | non V1 | tranchée |
| `searchable` / `unique` / `visibleWhen` | suivre règles existantes ; `visibleWhen` ok ; `requiredWhen` non | hypothèse | hypothèse |
| Signal d’appelant dans `UpdateDossierFieldsUseCase` | enum / booléen « workflowMapping » / port dédié | à concevoir | ouverte (conception) |

## Conception

- Faits vérifiés dans le code ou les sources (fichier, méthode) : _(après validation du cadrage)_
- Composants touchés (domaine, ports, adaptateurs, frontend, MCP) :
- Scénarios de test (unitaire, intégration, chaîne HTTP et sécurité, Playwright) :

## Lots

| Lot | Contenu | Statut (à faire, en cours, livré) | PR |
|---|---|---|---|

Prochaine action : attendre la validation du cadrage par l'utilisateur, puis concevoir (registre + lots).

## Preuves

| Date | Commande | Résultat |
|---|---|---|

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
