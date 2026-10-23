# §8 — Validation des champs dynamiques des dossiers

> **Statut :** clos · **Section :** [Décisions actées › Architecture](section-decisions-architecture.md) · ancien `TODO.md` §8 · [registre](README.md)

**Problème :** `Dossier.fields` n'était pas validé contre le schéma `FieldDefinition` du `DossierType` associé.

**Décision retenue :** Validation aux deux niveaux — couche REST pour le retour d'erreurs par champ (400) à des fins d'UX, et use case (`CreateDossierUseCase`/`CreateDossierBatchUseCase`) comme garde-fou faisant autorité, car les triggers/imports batch contournent la couche REST. Un service domaine pur `DossierFieldValidator` vérifie les champs requis, la conformité de type, et les valeurs `SELECT` hors énumération. Les champs inconnus sont rejetés par défaut, avec un indicateur `allowUnknownFields` sur `DossierType` pour les cas de compatibilité ascendante volontaire.

**Documenté dans :** [04-dossiers.md](../specs/04-dossiers.md).
