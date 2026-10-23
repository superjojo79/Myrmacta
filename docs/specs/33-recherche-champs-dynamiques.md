# Recherche sur les champs dynamiques (D2)

> **Statut : première analyse (2026-09-28), aucune décision technique tranchée.** Lot **D2**
> `recherche-champs-dynamiques` de la Vague D (`ROADMAP.md` Catégorie 7). Séquencé **après D1**
> ([32](32-modele-champs-enrichi.md)). À affiner en `dev-lifecycle` Phase 1.

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Filtres | `status`, `dossierTypeCode` et **préfixe** de `reference` (`LIKE 'x%'`, métacaractères échappés) | `DossierSpecifications` |
| API | `GET /api/dossiers?status=&dossierTypeCode=&reference=&page=&size=&sort=` | `DossierController` |
| MCP | `search_dossiers` : mêmes critères | `SearchDossiersTool` |
| Stockage | `fields` en **`json`**, pas `jsonb` : pas d'index GIN possible en l'état | `001-dossier-tables.yaml` |
| Index | Index de liste existants sur les colonnes fixes | `008-dossier-list-indexes.yaml` |
| Tests | Suite d'intégration sur **H2** (`MODE=PostgreSQL`), Testcontainers PostgreSQL ciblé (D-TESTCONTAINERS) | `src/test/resources/application.properties`, PR #66 |

**Conséquence :** impossible de retrouver « le dossier de M. X » ou « le sinistre sur le contrat
Y ». Bloquant au-delà de quelques milliers de dossiers par tenant.

## 2. Objectif

- Filtrer sur des champs dynamiques déclarés cherchables (égalité, intervalle pour dates et
  montants, appartenance pour `SELECT`).
- Recherche plein texte sur la référence et les champs texte.
- Performances stables sur des volumes réalistes (ordre de grandeur à fixer en Phase 1).

## 3. Périmètre à poser (Phase 1)

- Attribut `searchable` sur `FieldDefinition` (ou tous les champs non sensibles par défaut).
- Migration `fields` `json` → **`jsonb`** (sauf si D1 l'a déjà faite), index GIN.
- Plein texte : `tsvector` généré (colonne ou index d'expression), configuration linguistique.
- Syntaxe de filtre côté API (paramètres typés vs mini-langage) ; pagination inchangée.

## 4. Coutures et dépendances

- `FieldDefinition` partagée avec D1 → **après D1**, pas en parallèle.
- **Masquage** : un champ `sensitive` n'est pas cherchable, ou seulement par égalité stricte,
  sinon la recherche devient un oracle (on devine une valeur masquée par essais successifs).
- **ACL de type** (`DossierAccessPolicy`) : les filtres s'appliquent après le périmètre de type.
- **Tests** : opérateurs JSONB et `tsvector` absents de H2 → extension ciblée de
  D-TESTCONTAINERS, sans basculer toute la suite.
- **Migration** : `json` → `jsonb` sur chaque schéma tenant (Liquibase), durée sur gros volume.

## 5. Exposition MCP

Étendre `search_dossiers` avec les mêmes filtres que l'API, dans ce lot (principe P1 de
l'[analyse 46](46-exposition-mcp-couche-low-code.md)). Même masquage que l'API.

## 6. Risques

- Oracle sur champs sensibles (voir ci-dessus).
- Requêtes coûteuses : borner le nombre de filtres combinés et exiger au moins un critère indexé.
- Divergence H2 / PostgreSQL sur la syntaxe.

## 7. Décisions à trancher

1. **D-RECH-SCOPE** — champs cherchables : opt-in `searchable` ou tous les non sensibles.
2. **D-RECH-SENSITIVE** — sensible exclu de la recherche, ou égalité stricte seulement.
3. **D-RECH-FTS** — plein texte dans ce lot ou dans un second temps ; langue(s).
4. **D-RECH-SYNTAX** — paramètres typés ou mini-langage de filtre.

## 8. Documentation impactée

`docs/guides/GUIDE-UTILISATEUR.md` (recherche), `openapi/` (golden), `13-modele-physique.md` (index),
`04-dossiers.md`.
