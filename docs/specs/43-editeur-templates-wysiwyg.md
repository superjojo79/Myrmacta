# Éditeur WYSIWYG des templates de documents (LC4b)

> **Statut : première analyse (2026-09-28), aucune décision technique tranchée.** Sous-lot
> **LC4b** `editeur-templates-wysiwyg` de la couche low-code (D0 = C,
> [31](31-positionnement-produit.md), rang 4). À affiner en `dev-lifecycle` Phase 1.

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Saisie | HTML Thymeleaf dans un `<textarea>` | `TemplatesAdminView.vue` |
| Aperçu | HTML en temps réel et PDF (`POST /api/documents/preview`, `/preview/pdf`) | `TemplatePreviewPanel.vue`, `DocumentGenerationController` |
| Rendu PDF | Flying Saucer + OpenPDF : **CSS 2.1**, en-têtes / pieds, pagination | [07-generation-documents.md](07-generation-documents.md) |
| Expressions | Évaluateur allowlist (`#vars`, `#dates`, `#numbers`, `#strings`, `#lists` + chemins du modèle) | §40, `SafeThymeleafExpressionSupport` |
| Versions | Versions, variantes par langue et contexte, statut `DRAFT` / actif | [08-templates-documents.md](08-templates-documents.md), `TemplateStatus` |
| MCP | Lecture seule (`list_document_templates`) | analyse 46 §2 |

## 2. Objectif

Qu'un utilisateur métier modifie ses courriers : texte mis en forme, variables du dossier insérées
par menu, blocs conditionnels et répétés simples, aperçu PDF fidèle.

## 3. Périmètre à poser (Phase 1)

- **Bibliothèque** : TipTap (MIT, sur ProseMirror) ou GrapesJS (BSD-3).
- **Sous-ensemble rendu** : l'éditeur ne propose **que** ce que Flying Saucer rend (CSS 2.1 :
  pas de flexbox ni de grid) ; sinon l'aperçu et le PDF divergent.
- **Variables** : menu alimenté par le schéma du type (et les groupes répétables de D1) ; blocs
  « si » et « pour chaque » traduits en attributs Thymeleaf acceptés par l'allowlist §40.
- **Aller-retour** : un template écrit à la main en HTML reste-t-il éditable en WYSIWYG ?
  (mode source conservé pour les intégrateurs).
- **Versions** : l'édition crée une version `DRAFT`, publiée ensuite (cohérent avec D6).

## 4. Coutures

`TemplatesAdminView.vue`, `TemplatePreviewPanel.vue`, use cases de version de template, allowlist
§40 **inchangée** (l'éditeur produit du HTML qu'elle accepte déjà), variantes par langue.

## 5. Exposition MCP

**Nouveaux tools** `define_document_template` (version brouillon) et `preview_document` (réutilise
l'aperçu REST) — comble le trou actuel. Contrat = HTML Thymeleaf, pas le JSON de l'éditeur (P5).
Écriture soumise à D6 ([37](37-socle-mcp-low-code.md)).

## 6. Risques

- Divergence aperçu / PDF si l'éditeur autorise du CSS non rendu.
- HTML produit refusé par l'allowlist §40 → test de non-régression sur les constructions
  générées par l'éditeur.
- Perte de mise en forme sur un template HTML existant ouvert en WYSIWYG.

## 7. Décisions à trancher

1. **D-TPLED-LIBRARY** — TipTap ou GrapesJS.
2. **D-TPLED-BLOCKS** — blocs conditionnels et répétés dans la première tranche.
3. **D-TPLED-ROUNDTRIP** — templates HTML existants : ouverts en WYSIWYG ou restés en mode source.

## 8. Documentation impactée

`docs/guides/GUIDE-UTILISATEUR.md` (templates), `08-templates-documents.md`.
