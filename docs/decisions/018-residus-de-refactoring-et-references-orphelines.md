# §18 — Résidus de refactoring et références orphelines

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · ancien `TODO.md` §18 · [registre](README.md)

**Problème :** après les décisions n° 14–17, trois écarts ponctuels empêchaient d'utiliser les extraits comme spécification : `getWebhookAuthType()` inexistant sur `ConnectorInstanceData` ; `DocumentGenerationService` / `DocumentStorageService` absents de l'hexagone ; `updatedBy` affirmé par `12` mais absent de `Dossier` (et mauvaise attribution à `DossierElement`).

**Décision retenue — c1 + e1 + f1 :**

1. **c1 — `webhookAuthType` depuis `config`.** Lecture `WebhookAuthType.valueOf((String) instance.config().get("webhookAuthType"))` dans `WebhookController` — cohérent avec le `ConfigField` SELECT déjà documenté. Pas de champ de premier niveau sur le record.

2. **e1 — Aligner `08` sur l'hexagone.** `DocumentGenerationTask` injecte `GenerateDocumentUseCase` et `DocumentStoragePort` (définis dans `01` / `07`), plus de « Services » orphelins.

3. **f1 — `updatedBy` sur `Dossier`.** Champ ajouté au record, à l'entité JPA et aux mappers ; `withStatus` le propage. Pour `DossierElement`, `12` est corrigé : audit = `addedBy` / `addedAt` (pas `createdBy`/`updatedBy` — un élément est ajouté, pas mis à jour).

(Les résidus a/b/d/g étaient déjà corrigés par les décisions n° 14 et 16.)

**Documenté dans :** [docs/analyses/REVUE-PRE-IMPLEMENTATION.md](../analyses/REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [06-connecteurs.md](../specs/06-connecteurs.md), [08-templates-documents.md](../specs/08-templates-documents.md), [04-dossiers.md](../specs/04-dossiers.md), [12-autorisation-conformite.md](../specs/12-autorisation-conformite.md).
