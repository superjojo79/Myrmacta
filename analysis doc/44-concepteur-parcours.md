# Concepteur de parcours compilé en BPMN (LC5)

> **Statut : première analyse (2026-09-28), aucune décision technique tranchée.** Sous-lot
> **LC5** `concepteur-parcours` de la couche low-code (D0 = C, [31](31-positionnement-produit.md),
> rang 5). **Dépend de LC1** ([39](39-palette-metier-bpmn.md)) **et LC2**
> ([40](40-langage-expression-metier.md)). À affiner en `dev-lifecycle` Phase 1.

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Modélisation | BPMN seul, via `bpmn-js` ou upload | `/admin/bpmn-editor`, `/admin/workflow-types` |
| Brouillons | `BpmnDraft` persisté | table `bpmn_draft` |
| Statuts | `statusDefinitions` portés par la configuration de workflow | `workflow_type_config` |
| Validation | Parseur BPMN + scanner de surfaces UEL au déploiement | §57, §69 |

## 2. Objectif

Une vue **par étapes**, lisible par un métier : une suite d'étapes, chacune avec son responsable,
ses validations, ses conditions de passage et ses actions (document, notification, connecteur,
portail, décision DMN). La vue **génère le BPMN** ; Flowable reste le moteur ; l'éditeur BPMN
reste disponible pour les experts.

## 3. Périmètre à poser (Phase 1)

- **Modèle de parcours** : nouvel artefact du domaine (étapes, transitions, actions = briques de
  LC1, conditions = expressions LC2).
- **Compilation** : parcours → BPMN déterministe, validé par le parseur existant ; aucun XML
  que le parseur refuserait.
- **Aller-retour** (point dur) : un BPMN retouché à la main ne se relit pas toujours en parcours.
  Options : verrouiller un workflow dans un mode, ou conversion à sens unique (parcours → BPMN).
- **Expressivité** : parallélisme, boucles de correction, délais, escalades — lesquels dans la
  première tranche.
- **Visualisation** : l'instance en cours reste affichée en BPMN coloré (existant) ou aussi en
  vue parcours.

## 4. Coutures

Nouveau stockage de parcours (brouillon + versions), use case de compilation, déploiement via le
chemin existant, écran dédié. Aucune modification du moteur ni de l'allowlist.

## 5. Exposition MCP

`define_parcours` (brouillon), `compile_parcours` (retourne le BPMN sans déployer), publication
selon D-MCP-LC-PUBLISH. Avec une conversion à sens unique, l'agent n'édite que le parcours.
Voir [46](46-exposition-mcp-couche-low-code.md) §4.

## 6. Risques

- Coût élevé (taille L) et dette de double représentation.
- Attentes irréalistes : le parcours ne couvrira pas tout BPMN.

## 7. Décisions à trancher

1. **D-PARCOURS-ROUNDTRIP** — verrouillage par mode ou conversion à sens unique.
2. **D-PARCOURS-SCOPE** — constructions supportées dans la première tranche.
3. **D-PARCOURS-VIEW** — suivi des instances en vue parcours ou seulement en BPMN.

## 8. Documentation impactée

`GUIDE-UTILISATEUR.md` (nouvel écran), `GUIDE-CONCEPTION-BPMN.md` (lien parcours / BPMN),
`14-edition-bpmn.md`.
