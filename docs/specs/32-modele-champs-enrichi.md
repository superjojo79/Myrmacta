# Modèle de champs dynamiques enrichi (D1)

> **Statut : première tranche livrée (TODO §76, 2026-09-30)** — `MONEY`, `REPEATABLE_GROUP`, `EXTERNAL_REF` ; décisions D-CHAMPS-* tranchées dans
> `docs/decisions/roadmap-2026/DECISIONS.md` ; champs calculés et visibilité conditionnelle : lot suivant (après LC2, désormais livré).
> *(Ancien statut : première analyse du 2026-09-28.)* Lot **D1**
> `modele-champs-enrichi` de la Vague D (`ROADMAP.md` Catégorie 7). À affiner en `dev-lifecycle`
> Phase 1 avant tout code. Contexte : [31-positionnement-produit.md](31-positionnement-produit.md).

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Types de champ | `STRING, NUMBER, DATE, BOOLEAN, SELECT, FILE` | `domain/dossier/model/FieldType.java` |
| Définition | `FieldDefinition(name, label, type, required, defaultValue, options, group, sensitive)` — pas de précision, de devise, de sous-champs, de source externe, de règle | `FieldDefinition.java` |
| Validation | `DossierFieldValidator` (domaine) | `domain/dossier/model/DossierFieldValidator.java` |
| Stockage | Colonne `fields` de type **`json`** (pas `jsonb`), `Map<String, Object>` | `001-dossier-tables.yaml`, `DossierJpaEntity` |
| Compatibilité de schéma | Blocage 409 si le nouveau schéma est incompatible avec des dossiers existants | PR #87, TODO §47 |
| Consommateurs du schéma | Formulaires `dossier-fields` (§52), questions de tâche (`user-question`, §55), portail (§68), templates PDF, export CSV et BI (§65), masquage `sensitive` (§23), MCP `define_dossier_type` | — |

## 2. Objectif et cas d'usage

Permettre de décrire les dossiers réels sans détourner `STRING` ou `NUMBER` :

- **sinistres** : garanties, postes de préjudice (lignes), montants ;
- **subventions** : lignes budgétaires (montant, nature, justificatif) ;
- **onboarding fournisseur / client** : référence au compte dans l'ERP ou le CRM.

## 3. Périmètre à poser (Phase 1)

| Ajout | Contenu envisagé | Remarque |
|---|---|---|
| `MONEY` | Montant décimal + devise ISO 4217 | Pas de `double` : `BigDecimal` sérialisé en chaîne, échelle par devise |
| Groupe répétable | Liste de lignes typées (sous-champs) | Bornes min / max de lignes ; un niveau d'imbrication seulement ? |
| `EXTERNAL_REF` | Système + identifiant (+ libellé mis en cache) | La résolution (appel ERP) relève de D3, pas de D1 |
| Champs calculés | Valeur dérivée d'autres champs | **Via LC2** ([40](40-langage-expression-metier.md)), donc après LC2 |
| Visibilité / obligation conditionnelle | Règle sur d'autres champs | **Via LC2**, après LC2 |

Les trois premiers types sont indépendants de LC2 et peuvent partir en premier.

## 4. Coutures et dépendances

- **Domaine** : `FieldType`, `FieldDefinition`, `DossierFieldValidator`, règle de compatibilité
  409 (un groupe répétable ajouté à un type existant doit rester compatible).
- **Rendu** : formulaires `dossier-fields`, `user-question`, portail ; composants du SPA.
- **Documents** : variables Thymeleaf pour les lignes (itération) — l'évaluateur allowlist
  (`SafeThymeleafExpressionSupport`, §40) doit accepter l'itération sur un groupe sans s'ouvrir.
- **Exports** : CSV dossiers et BI — aplatissement d'un groupe répétable à trancher.
- **Recherche** : D2 ([33](33-recherche-champs-dynamiques.md)) partage `FieldDefinition` →
  séquencer D2 **après** D1.
- **Sensible** : un sous-champ peut-il être `sensitive` seul ?
- **Parallélisable** avec LC1 ([39](39-palette-metier-bpmn.md)) et D3a : surfaces disjointes.

## 5. Exposition MCP

`define_dossier_type`, `get_dossier_type`, `create_dossier`, `get_dossier` et
`define_task_question_schema` portent déjà `fieldDefinitions` et les valeurs : les étendre aux
nouveaux types **dans ce lot** (principe P1 de l'[analyse 46](46-exposition-mcp-couche-low-code.md)).
Format du contrat = format du domaine (P5).

## 6. Risques

- Rupture de contrat API et MCP pour les consommateurs qui supposent des valeurs scalaires.
- Volume : un groupe répétable non borné gonfle la colonne `fields` et les exports.
- Arrondis monétaires dans les templates et les exports.

## 7. Décisions à trancher

1. **D-CHAMPS-TYPES** — liste exacte des nouveaux types de la première tranche.
2. **D-CHAMPS-REPEAT** — imbrication autorisée (un niveau ou plus) et bornes de lignes.
3. **D-CHAMPS-MONEY** — une devise par champ fixe ou choisie par valeur ; échelle.
4. **D-CHAMPS-EXPORT** — aplatissement des groupes répétables en CSV (colonnes indexées, fichier
   séparé, JSON dans la cellule).
5. **D-CHAMPS-STORAGE** — conserver `json` ou migrer `fields` en `jsonb` dès D1 (utile à D2).

## 8. Documentation impactée

`docs/guides/GUIDE-UTILISATEUR.md` (Admin → Types), `docs/guides/GUIDE-CONCEPTION-BPMN.md` (référencement des champs),
`04-dossiers.md`, `17-schema-champs-dynamiques.md`, `openapi/` (golden).
