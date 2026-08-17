---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-17
updated: 2026-08-17
feature: preview-documents
phase1: 2026-08-17
phase2: 2026-08-17
stakeholder_decisions: 2026-08-17
todo_ref: D-PREVIEW (templates-documents / generation-documents) · analysis doc/07
---

# Requirements & Problem Understanding — `preview-documents`

> Identifiée comme prochaine feature après clôture de `elements-dossier` (PR #51).
> Lot **D-PREVIEW** différé depuis `templates-documents` → `generation-documents`
> (« lot suivant ») → `frontend` (jamais repris). Spécifié dans
> [07-generation-documents.md](../../../analysis%20doc/07-generation-documents.md)
> § « Preview des documents ».

## Problem Statement
**What problem are we solving?**

FlowForge sait **générer un PDF** (`POST /api/documents/generate`, Flying Saucer)
et **administrer des templates Thymeleaf** (`/admin/templates`), mais un
administrateur ne peut **pas voir le rendu pendant la saisie**. L'écran admin
n'expose qu'un `<textarea>` brut. Pour valider mise en page, variables
Thymeleaf et pagination A4, il faut activer le template puis appeler
`/generate` — lent, sans simulation écran, et le PDF de `/generate` n'offre pas d'enveloppe HTML A4 ni d'UI
d'aperçu pendant la saisie.

- **Affectés :** `TENANT_ADMIN` (auteur de templates), `WORKFLOW_ADMIN` /
  `DOSSIER_MANAGER` (déjà autorisés à générer un PDF pour valider un modèle).
- **Situation actuelle :** pipeline resolve → Thymeleaf → PDF livré ; aucun
  endpoint `/preview` ni `/pdf` inline ; aucun composant UI d'aperçu.
- **Réf. :** D-PREVIEW ; `analysis doc/07` (stratégie hybride HTML + PDF) ;
  README (« Preview en temps réel pendant la saisie, aperçu PDF avant
  validation ») ; `11-frontend.md` (preview documentaire).

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- **Preview HTML temps réel** — `POST /api/documents/preview` retourne le HTML
  Thymeleaf évalué, enveloppé du CSS print-preview A4 de l'analyse 07, **sans
  persistance**.
- **Aperçu PDF inline** — `POST /api/documents/preview/pdf` retourne le même
  rendu Flying Saucer que `/generate`, avec `Content-Disposition: inline`,
  **sans persistance**.
- **Source live** (D-SOURCE) : le body peut fournir `htmlContent` (saisie en
  cours, template non enregistré) **ou** `templateKey` (résolution ACTIVE,
  même cascade que generate). Si les deux sont présents, `htmlContent` gagne.
- **Authz** (D-AUTHZ = **B**) : les deux endpoints exigent une session BFF +
  CSRF et l'un de `TENANT_ADMIN` | `WORKFLOW_ADMIN` | `DOSSIER_MANAGER`.
  **En plus**, si la source retenue est le live (`htmlContent` non blank —
  D-SOURCE), seul `TENANT_ADMIN` est accepté (sinon 403). Un `templateKey`
  seul reste ouvert aux trois rôles, comme `/generate`.
- **UI admin templates** : panneau d'aperçu à côté du formulaire de saisie
  (`TemplatePreviewPanel`) — HTML rafraîchi avec debounce 400 ms, PDF sur
  action explicite.

### Secondary goals

- Variables d'échantillon (`variables` JSON) pour un rendu réaliste, filtrées
  par `DocumentVariableFilter` (mêmes clés plateforme exclues que generate).
- Réutiliser le **mécanisme** anti-abus de generate (fenêtre in-memory +
  filtre taille 256 Kio). **D-RATE-SPLIT = C :** quota HTML `/preview`
  dédié 120/min ; `/preview/pdf` partage le 30/min de `/generate`.
- Couverture Jacoco / Vitest projet (≥ 80 % zones touchées) + Playwright
  (contrat API consommé par le SPA).

### Non-goals (explicitement hors scope)

- Adaptateur Playwright / CSS3 avancé pour le moteur PDF (D-ENGINE déjà
  tranché : Flying Saucer).
- Stockage objet / attach `PRODUCED_DOC` depuis le preview (même règle que
  generate HTTP : mémoire uniquement).
- Preview depuis le détail dossier ou la visualisation workflow.
- Éditeur WYSIWYG / IDE templates (on enrichit le formulaire existant).
- `API_SERVICE` / M2M (A7 generate, toujours différé).
- OpenAPI formel (reste TODO §24).
- Antivirus, rétention, purge (§21–22, §30).
- Modification de `POST /api/documents/generate` (contrat inchangé).

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| HTML seul vs PDF seul vs hybride analyse 07 | HTML = itération rapide ; PDF = vérité A4 | **Hybride** (D-SCOPE) |
| Preview uniquement template sauvé vs htmlContent live | Sauvé = round-trip activate ; live = « pendant la saisie » | **Live + resolve** (D-SOURCE) |
| Étendre `/generate` (query `inline`) vs endpoints dédiés | `/generate` déjà rate-limité et testé ; mélange HTML/PDF | **Endpoints dédiés** `/preview` + `/preview/pdf` |
| Authz live vs template publié | Live = HTML arbitraire ; generate n'accepte qu'un ACTIVE | **B** (D-AUTHZ) — live = TA ; `templateKey` = TA/WA/DM |
| PDF à chaque frappe vs bouton | Flying Saucer coûteux | **HTML debounce / PDF on-demand** (D-PDF-UX) |

## User Stories & Use Cases

1. **En tant que** `TENANT_ADMIN`, **je veux** voir le HTML mis en page A4
   pendant que je tape le template, **afin de** corriger marges et variables
   sans enregistrer ni activer.
2. **En tant que** `TENANT_ADMIN`, **je veux** un aperçu PDF dans un iframe,
   **afin de** valider le rendu Flying Saucer avant activation.
3. **En tant que** `TENANT_ADMIN` / `WORKFLOW_ADMIN` / `DOSSIER_MANAGER`,
   **je veux** prévisualiser un template ACTIVE (`templateKey` seul) avec des
   variables d'échantillon, **afin de** vérifier un modèle publié sans
   démarrer un workflow. (L'UI V1 reste `/admin/templates`, `TENANT_ADMIN`
   seulement ; WA/DM passent par l'API.)
4. **En tant qu'** ops, **je veux** que preview ne stocke rien, **afin de** ne
   pas créer d'objets orphelins (aligné D-HTTP-STORE).

### Edge cases

- `htmlContent` et `templateKey` tous deux vides / blancs → 400
  `TemplateValidationException`.
- `templateKey` inconnu (sans `htmlContent`) → 404 `TemplateNotFoundException`.
- Thymeleaf invalide → 400 `TemplateProcessingException`.
- Rendu PDF en échec → 400 `DocumentRenderException` (même mapping generate).
- `htmlContent` > 5 Mio (même borne que create/update template) → 400.
- Corps HTTP au-delà de `app.documents.generate.max-body-bytes` → 413 (filtre).
- Burst HTML au-delà de `app.document.preview.rate-limit-per-minute` (120) →
  429. Burst PDF preview **ou** generate au-delà de 30/min **cumulés** → 429.
- `DOSSIER_VIEWER` / anonyme → 403 / 401 (aucun des deux endpoints).
- `WORKFLOW_ADMIN` / `DOSSIER_MANAGER` avec `htmlContent` non blank → **403**
  (chemin live, D-AUTHZ B), même si `templateKey` est aussi fourni (D-SOURCE :
  le live gagne, donc la gate TA s'applique).
- `TENANT_ADMIN` avec `htmlContent` non blank → 200 (chemin live).
- Variables `null` → `{}` ; clés plateforme filtrées.
- `javascript:` / HTML script dans le template : l'iframe HTML est `sandbox`
  sans scripts (défense UI) ; le PDF reste protégé par
  `RestrictedResourceUserAgent` (SSRF, déjà livré).

## Success Criteria
**How will we know when we're done?**

- [ ] `POST /api/documents/preview` : 200 `text/html` ; CSS print-preview
      présent ; aucun appel `DocumentStoragePort`.
- [ ] `POST /api/documents/preview/pdf` : 200 `application/pdf` ;
      `Content-Disposition: inline` ; commence par `%PDF` ; aucun store.
- [ ] Live `htmlContent` évalué sans passer par `ResolveDocumentTemplateUseCase`.
- [ ] `templateKey` sans `htmlContent` suit la cascade ACTIVE existante.
- [ ] Authz D-AUTHZ B : TA live → 200 ; WA/DM live → 403 ; TA/WA/DM
      `templateKey` seul → 200 ; VIEWER → 403 ; anonyme → 401.
- [ ] Rate limit + taille max étendus aux deux chemins preview.
- [ ] Admin templates : aperçu HTML après saisie (debounce) + bouton PDF.
- [ ] Iframe HTML `sandbox` (pas de scripts).
- [ ] `./mvnw verify` vert ; Vitest vert ; Playwright vert (nouveau scénario
      admin templates + mocks preview).
- [ ] `DEFERRED-FEATURES.md` : D-PREVIEW marqué livré.

## Constraints & Assumptions

- Hexagone : pas de Spring/Thymeleaf/Flying Saucer dans `domain`.
- Réutiliser `HtmlTemplateProcessor`, `PdfRenderer`,
  `ResolveDocumentTemplateUseCase`, `DocumentVariableFilter` — pas de nouveau
  moteur.
- SPA même origine, CSRF cookie (décision n° 15 / 27).
- Une instance = un tenant (décision n° 13).
- **Hypothèse D-UI-CREATE :** le formulaire de création existant est le lieu
  de saisie V1 ; pas d'éditeur plein écran pour les versions déjà listées
  (un `htmlContent` collé ou un `templateKey` suffisent).
- **Hypothèse D-CSS-WEB :** le wrapper CSS print-preview est une
  préoccupation de présentation (adaptateur web), pas du domaine.
- Task tracing `ai-devkit task` indisponible dans cet environnement
  (`unknown command 'task'`) — lifecycle poursuivi sans journal de tâches.

## Stakeholder decisions

| Id | Décision | Choix |
|---|---|---|
| **D-SCOPE** | Modes d'aperçu | **Hybride** HTML temps réel + PDF inline (analyse 07) |
| **D-SOURCE** | Source du HTML | `htmlContent` live **ou** resolve `templateKey` ; live gagne si les deux |
| **D-AUTHZ** | Rôles | **B** — live `htmlContent` = `TENANT_ADMIN` ; `templateKey` seul = TA \| WA \| DM |
| **D-STORE** | Persistance | **Jamais** (mémoire uniquement) |
| **D-HTML-UX** | Rafraîchissement HTML | Debounce **400 ms**, iframe `srcdoc` + `sandbox` |
| **D-PDF-UX** | Rafraîchissement PDF | **Bouton** « Aperçu PDF » (pas à chaque frappe) |
| **D-CSS** | CSS A4 | Injecté sur la réponse HTML seulement, extrait analyse 07 |
| **D-RATE** | Abuse | Filtre taille partagé ; D-RATE-SPLIT **C** — HTML 120/min dédié ; PDF preview partage generate 30/min |
| **D-UI** | Surface | `TemplatesAdminView` + `TemplatePreviewPanel` (pas le détail dossier) |
| **D-ENDPOINTS** | URLs | `POST /api/documents/preview` et `POST /api/documents/preview/pdf` |

## Questions & Open Items

**Q1 — D-AUTHZ : clos (2026-08-17), option B.**
**Q2 — D-RATE-SPLIT : clos (2026-08-17), option C** (détail dans le design).

Aucun autre point ouvert bloquant. Suivi non bloquant (hors V1) : preview
depuis le détail dossier.
