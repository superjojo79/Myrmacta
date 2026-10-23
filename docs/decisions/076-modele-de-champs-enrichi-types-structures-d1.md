# §76 — Modèle de champs enrichi — types structurés (D1) (livré)

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §76 · [registre](README.md)

**Énoncé :** lot **D1** de la Vague D, analyse [32-modele-champs-enrichi.md](../specs/32-modele-champs-enrichi.md). Docs SDD :
[requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-30-feature-d1-modele-champs-enrichi.md), [design](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-30-feature-d1-modele-champs-enrichi.md),
[planning](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-30-feature-d1-modele-champs-enrichi.md), [implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-30-feature-d1-modele-champs-enrichi.md),
[testing](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-30-feature-d1-modele-champs-enrichi.md). Journal : [DECISIONS.md](roadmap-2026/DECISIONS.md).

**Décisions (mandat « roadmap complète », 2026-09-30).** D-CHAMPS-TYPES = `MONEY`, `REPEATABLE_GROUP`, `EXTERNAL_REF` ; D-CHAMPS-MONEY = montant en **texte décimal exact** +
devise par valeur, refusé (jamais arrondi) au-delà des décimales de la devise ; D-CHAMPS-REPEAT = un niveau, 20 sous-champs, 0–500 lignes ; D-CHAMPS-SENSITIVE = sensibilité sur le
**groupe entier** ; D-CHAMPS-EXPORT = colonnes `.amount/.currency`, `.system/.id/.label`, groupe en une colonne JSON ; D-CHAMPS-STORAGE = `json` conservé (jsonb en D2) ;
D-CHAMPS-NORMALIZE ; D-CHAMPS-USERQ = types refusés dans les questions de tâche pour l'instant ; D-CHAMPS-REF = référence seule.

**Livré.** `FieldType` + `FieldDefinition` (règles de définition), `DossierFieldValidator` (validation avec chemins d'erreur `lignes[1].poste`, normalisation), `DossierFieldCsv`
(exports dossiers et BI), branchements création/mise à jour/MCP, SPA (saisie, lecture texte/tableau, diff sans faux patch, éditeur d'administration avec colonnes de groupe, validation
miroir). Preuves de non-régression : modèles PDF (sans modification), compatibilité 409, masquage du groupe sensible, OpenAPI additif. Aucune migration, aucune variable d'environnement.
**Impacte :** `docs/guides/GUIDE-UTILISATEUR.md` (Admin → Types), `docs/guides/GUIDE-CONCEPTION-BPMN.md` (§3sexies), `docs/specs/32`, `ROADMAP.md`, `CHANGELOG.md`. **Revue adversariale** : 1 passe, 0 Critique/Haute, Moyens et Bas corrigés avec tests ; résidu ⚠ M4 (plafond de corps HTTP non spécifique aux `fields`), voir le document d'implémentation.
