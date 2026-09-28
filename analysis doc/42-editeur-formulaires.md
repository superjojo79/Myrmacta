# Éditeur visuel de formulaires (LC4a)

> **Statut : première analyse (2026-09-28), aucune décision technique tranchée.** Sous-lot
> **LC4a** `editeur-formulaires` de la couche low-code (D0 = C, [31](31-positionnement-produit.md),
> rang 4). À affiner en `dev-lifecycle` Phase 1.

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Formulaires de tâche | Trois sortes : `dossier-fields` (champs du dossier), `document-generation`, `user-question:<schemaKey>` | §52, §55 |
| Schémas de questions | Liste de `fieldDefinitions` éditée champ par champ | `TaskQuestionSchemasAdminView.vue`, table `task_question_schema` |
| Portail externe | Sert uniquement des formulaires `user-question` | §68, D-PORTAL-FORM |
| Modèle | Même `FieldDefinition` que les types de dossier : pas de mise en page, de sections, d'aide contextuelle, de règles | `FieldDefinition.java` |

## 2. Objectif

Composer visuellement un formulaire (glisser-déposer, sections, colonnes, aide, aperçu) pour les
questions de tâche, les champs d'un type de dossier et le portail, sans éditer une liste brute.

## 3. Périmètre à poser (Phase 1)

- **Bibliothèque** : `form-js` (bpmn.io, même famille et même licence que `bpmn-js`), ou
  composants PrimeVue maison.
- **Contrat** : `fieldDefinitions` reste la source (principe P5 de
  l'[analyse 46](46-exposition-mcp-couche-low-code.md)) ; la mise en page est un **ajout** au
  schéma, pas un remplacement par le JSON de la bibliothèque.
- **Rendu** : le même moteur de rendu pour le back-office et le portail.
- **Règles** : visibilité / obligation conditionnelle via LC2 ([40](40-langage-expression-metier.md)).
- **Nouveaux types** : ceux de D1 ([32](32-modele-champs-enrichi.md)) dès leur livraison.

## 4. Coutures

`TaskQuestionSchemasAdminView.vue`, Admin → Types, rendu `dossier-fields` / `user-question`,
build portail `/portal/`, validation serveur inchangée (`DossierFieldValidator`). À coordonner
avec D1 si les nouveaux types arrivent en même temps.

## 5. Exposition MCP

Extension de `define_task_question_schema` et `define_dossier_type` pour la mise en page ;
`preview_form` optionnel. Pas de nouveau format au contrat MCP.

## 6. Décisions à trancher

1. **D-FORMED-LIBRARY** — `form-js` ou composants maison.
2. **D-FORMED-LAYOUT** — modèle de mise en page ajouté au schéma (sections, colonnes, aide).
3. **D-FORMED-SCOPE** — questions de tâche seules, ou aussi champs de type de dossier.

## 7. Documentation impactée

`GUIDE-UTILISATEUR.md` (schémas de questions, types), `22-formulaires-dynamiques-taches.md`,
`23-questions-utilisateur-taches.md`.
