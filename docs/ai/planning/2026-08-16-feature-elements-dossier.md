---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: in_progress
created: 2026-08-16
updated: 2026-08-17
feature: elements-dossier
design_aligned: 2026-08-17
phase4: 2026-08-17
sequencing: layered
---

# Project Planning & Task Breakdown — `elements-dossier`

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine** : `ElementType` élargi, garde de statut + validation par type dans
  `AddDossierElementUseCaseImpl`, `downloadable` généralisé
- [x] **M2 — API REST backend** : 2 nouveaux endpoints d'ajout, généralisation du endpoint de
  contenu, mapping d'exception, bornes upload (taille/MIME)
- [x] **M3 — Frontend** : `ElementsList.vue` avec formulaire "Ajouter" par type, client API,
  correction d'une dérive de typage FE découverte pendant la planification
- [ ] **M4 — Preuves & docs** : suite de tests complète (unit/intégration/Vitest/Playwright),
  `DEFERRED-FEATURES.md`/`TODO.md` mis à jour

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [x] **T1. `ElementType` — 4 nouvelles valeurs**
  - **Outcome :** `domain/dossier/model/ElementType.java` passe de
    `{PRODUCED_DOC}` à `{PRODUCED_DOC, ATTACHMENT, INFORMATION, NOTE, LINK}`. Aucune migration
    Liquibase (colonne `varchar(32)` sans `CHECK`, vérifié en Phase 1).
  - **Depends on :** —
  - **Validation :** compile.
  - **Tests liés :** —

- [x] **T2. `AddDossierElementUseCaseImpl` — validation par type + garde de statut**
  - **Outcome :** remplace la garde `Only PRODUCED_DOC is supported in V1` par une validation par
    type : `ATTACHMENT` (storageRef requis, tenant-préfixé — réutilise
    `requireValidStorageRef` existant), `INFORMATION` (metadata non vide), `NOTE`
    (`metadata.text` non vide), `LINK` (`metadata.url` — URI bien formée, jamais résolue).
    `label` requis pour tous (déjà le cas). Ajoute la garde de statut (décision design n°6) :
    si `ctx instanceof AccessContext.User` et `dossier.status()` ∈ `{CLOTURE, ARCHIVE}` →
    `DossierConflictException` (409) — réutilise la même liste `BLOCKED_STATUSES` que
    `UpdateDossierFieldsUseCaseImpl` (dupliquée en V1, pas d'extraction d'une constante partagée
    pour 2 usages — à revisiter si un 3e apparaît). `PRODUCED_DOC` (`AccessContext.System`)
    non concerné, aucune régression.
  - **Depends on :** T1
  - **Validation :** unitaires verts (mocks repository/policy), y compris non-régression
    `PRODUCED_DOC` (tests existants).
  - **Tests liés :** `AddDossierElementUseCaseImplTest` — tous scénarios testing § (unit tests).

- [x] **T3. `DossierElementView.from(...)` — `downloadable` généralisé**
  - **Outcome :** `downloadable = (type == PRODUCED_DOC || type == ATTACHMENT) && storageRef
    non vide` (au lieu du seul `PRODUCED_DOC`).
  - **Depends on :** T1
  - **Validation :** unitaires verts (extension du test existant).
  - **Tests liés :** `DossierElementView.from` — testing §.

### M2 — API REST backend

- [x] **T4. DTO `AddDossierElementRequest`**
  - **Outcome :** `record AddDossierElementRequest(ElementType type, String label,
    Map<String,Object> metadata)` dans `infrastructure/web/dossier/DossierApiDtos.java`, même
    fichier que les autres DTOs `Dossier*Request`.
  - **Depends on :** T1
  - **Validation :** compile.
  - **Tests liés :** —

- [x] **T5. `DossierController` — `POST /{id}/elements` (JSON)**
  - **Outcome :** nouvel endpoint pour `INFORMATION`/`NOTE`/`LINK`. Rejette (400,
    `IllegalArgumentException` → géré par le handler générique existant, cohérent avec
    `createRelation`) `type=PRODUCED_DOC`/`ATTACHMENT` avant même d'appeler le use case — message
    orientant vers l'endpoint dédié. Construit `AddDossierElementUseCase.AddCommand` avec
    `storageRef=null`, `workflowStepId=null`, `addedBy=actor.userId()`. Renvoie `201` +
    `DossierElementView` (même mapping que `listElements`, pas de nouveau DTO de réponse).
  - **Depends on :** T2, T4
  - **Validation :** MockMvc.
  - **Tests liés :** MockMvc — 201/400/403/404/409, régression `GET .../elements`.

- [x] **T6. `DossierElementUploadController` (nouveau, multipart) — `POST /{id}/elements/attachment`**
  - **Outcome :** nouveau contrôleur `infrastructure/web/dossier`, injecte
    `DocumentStoragePort`, `AddDossierElementUseCase`, `TenantProperties`,
    `AccessContextFactory`, propriétés d'allowlist MIME (T9). Valide `file` (présent, taille via
    limite multipart Spring native — `MaxUploadSizeExceededException` mappée en 413 par T7),
    type MIME (allowlist applicative → 400 si hors liste), `label` requis. Stocke via
    `DocumentStoragePort.store(tenantId, contentType, bytes)` **avant** d'appeler le use case
    (séquence acceptée en design, risque d'orphelin documenté). Construit `metadata =
    {"filename": ..., "contentType": ..., "sizeBytes": ...}`. Renvoie `201` + `DossierElementView`.
  - **Depends on :** T2, T9
  - **Validation :** MockMvc multipart.
  - **Tests liés :** MockMvc — upload réussi, fichier absent, MIME refusé (400), 403/409,
    `metadata` correct. Taille hors borne (413) **non couverte en MockMvc** — le contrôleur ne
    fait volontairement aucune vérification applicative de taille redondante avec
    `spring.servlet.multipart.max-file-size` (évite les deux bornes divergentes anticipées dans
    Risks) ; `MockMvcRequestBuilders.multipart(...).file(...)` construit une requête déjà parsée
    qui ne traverse jamais l'enforcement Tomcat réel — mapping 413 vérifié directement en test
    unitaire sur `DossierExceptionHandler.handleUploadTooLarge`.

- [x] **T7. `DossierExceptionHandler` — mapping `InvalidDossierElementException` + `MaxUploadSizeExceededException`**
  - **Outcome :** ajoute `@ExceptionHandler(InvalidDossierElementException.class)` → 400 (absent
    aujourd'hui pour `web.dossier`, seul `DocumentGenerationExceptionHandler` le gérait côté
    `web.document`) et `@ExceptionHandler(MaxUploadSizeExceededException.class)` → 413.
  - **Depends on :** —
  - **Validation :** MockMvc dédié (évite une régression silencieuse vers 500, rappelée par le
    testing doc).
  - **Tests liés :** testing § — mapping 400/413 dédié.

- [x] **T8. `DossierElementContentController` — généralisation `Content-Type`/nom de fichier**
  - **Outcome :** sert désormais `PRODUCED_DOC` (inchangé, `application/pdf` en dur, nom
    `<label>.pdf`) **et** `ATTACHMENT` (Content-Type et nom de fichier tirés de
    `metadata.contentType`/`metadata.filename`, fallback `application/octet-stream` /
    `<label>` si absents). `INFORMATION`/`NOTE`/`LINK` → `InvalidDossierElementException`
    inchangé (déjà le comportement pour tout élément sans `storageRef`).
  - **Depends on :** T1, T3
  - **Validation :** MockMvc — non-régression `PRODUCED_DOC` + nouveau cas `ATTACHMENT`.
  - **Tests liés :** testing § — content endpoint, tous les cas listés.

- [x] **T9. Bornes upload — propriétés Spring + allowlist MIME configurable**
  - **Outcome :** `spring.servlet.multipart.max-file-size=25MB` /
    `max-request-size=25MB` + `resolve-lazily: true` dans `application.yml` (propriété, pas de
    filtre dédié — décision design n°3 ; `resolve-lazily` nécessaire pour que
    `MaxUploadSizeExceededException` soit levée depuis l'intérieur du contrôleur, dans le scope du
    `DossierExceptionHandler` `basePackages`-scopé, plutôt que pendant le dispatch avant résolution
    du handler). Nouveau `@ConfigurationProperties(prefix = "app.dossier.attachment")`
    (`AttachmentUploadProperties`, record **`allowedContentTypes` seul** — pas de `maxSizeBytes`
    dupliqué : écarté en cours d'implémentation après avoir constaté que le contrôleur ne doit
    faire *aucune* vérification de taille applicative redondante avec la config Spring, exactement
    le risque de double seuil anticipé dans la table Risks) dans `infrastructure.config`, même
    patron que `StorageProperties`. Wiring `DossierConfig` :
    `@EnableConfigurationProperties(AttachmentUploadProperties.class)` ;
    `DossierElementUploadController` est un `@RestController` auto-détecté, pas un bean manuel.
  - **Depends on :** —
  - **Validation :** contexte Spring monte (IT boot existante).
  - **Tests liés :** testing § — rejet MIME (400, MockMvc) ; rejet taille (413) vérifié en
    isolation sur `DossierExceptionHandler.handleUploadTooLarge` (non couvrable en MockMvc, voir
    T6).

### M3 — Frontend

- [x] **T10. Correction dérive `ElementType` côté FE (bug trouvé en planification)**
  - **Outcome :** `frontend/src/api/types.ts` déclare aujourd'hui
    `'PRODUCED_DOC' | 'ATTACHMENT' | 'NOTE' | 'EXTERNAL_REF'` — ni aligné sur le backend actuel
    (`PRODUCED_DOC` seul en V1) ni sur la cible de cette feature (`LINK`, pas `EXTERNAL_REF` ;
    `INFORMATION` manquant), cf. `analysis doc/04-dossiers.md`. Corrigé en
    `'PRODUCED_DOC' | 'ATTACHMENT' | 'INFORMATION' | 'NOTE' | 'LINK'`. Pas une régression de
    cette feature (le type était déjà faux avant), mais bloquant pour écrire un FE correct —
    corrigé dans cette tranche plutôt que de perpétuer l'erreur.
  - **Depends on :** —
  - **Validation :** `vue-tsc --noEmit`.
  - **Tests liés :** couvert indirectement par T13/T15 (aucun scénario n'exercerait
    `EXTERNAL_REF`, qui disparaît).

- [x] **T11. Client API FE — `addElement` (JSON) et `uploadAttachment` (multipart)**
  - **Outcome :** `addElement(dossierId, {type, label, metadata})` ajouté dans
    `frontend/src/api/dossiers.ts` (à côté de `listDossierElements`, pas dans `api/elements.ts`
    qui ne contient aujourd'hui que le téléchargement de contenu — correction du design doc,
    simple regroupement par fichier existant, pas un écart fonctionnel).
    `uploadAttachment(dossierId, file, label)` ajouté dans `frontend/src/api/elements.ts`
    (`FormData`, `multipart/form-data`).
  - **Depends on :** T10
  - **Validation :** compile + Vitest (mock axios).
  - **Tests liés :** testing § — couverture indirecte via T15 (composant) et T16 (e2e).

- [x] **T12. `stores/dossier.ts` — actions d'ajout**
  - **Outcome :** `addElement`/`uploadAttachment` : appellent le client API (T11) puis
    `refreshRelated()` (pattern déjà existant après `createDossierRelation`) pour rafraîchir
    `elements`. Propagation d'erreur via `toProblemDetail` (pattern existant).
  - **Depends on :** T11
  - **Validation :** Vitest store.
  - **Tests liés :** testing § — rafraîchissement de la liste après ajout (composant/e2e).

- [x] **T13. `ElementsList.vue` — formulaire "Ajouter" activé**
  - **Outcome :** bouton "Ajouter" activé (retrait du `disabled`) ; sélecteur de type → 4
    sous-formulaires (file picker `ATTACHMENT`+label, clé/valeur libre `INFORMATION`, zone de
    texte `NOTE`, URL+description `LINK`) ; état de chargement/erreur (pattern existant du
    composant dossier) ; généralisation du téléchargement — `download()` utilise
    `metadata.filename`/`contentType` réels pour `ATTACHMENT` au lieu du `.pdf` en dur actuel
    (non-régression `PRODUCED_DOC` conservée).
  - **Depends on :** T3, T12
  - **Validation :** Vitest composant (`ElementsList.spec.ts`, existant à étendre).
  - **Tests liés :** testing § — tous les scénarios Vitest listés.

### M4 — Preuves & docs

- [ ] **T14. Suite backend complète**
  - **Outcome :** `./mvnw verify` vert (tests + ArchUnit + Jacoco ≥ seuil existant), tous les
    scénarios unit/intégration du testing doc couverts.
  - **Depends on :** T1–T9
  - **Validation :** commande CI locale.
  - **Tests liés :** tous scénarios backend restants.

- [ ] **T15. Suite frontend Vitest**
  - **Outcome :** `cd frontend && npm run test -- --coverage` vert.
  - **Depends on :** T10–T13
  - **Validation :** commande CI locale.
  - **Tests liés :** tous scénarios Vitest listés.

- [ ] **T16. Playwright e2e**
  - **Outcome :** `cd frontend && npm run test:e2e` vert — parcours ajout pièce jointe/note/lien,
    régression téléchargement `PRODUCED_DOC`, message d'erreur upload trop volumineux. Rappel
    `flowforge-conventions` : obligatoire ici (SPA + contrat API touchés), pas un nice-to-have.
  - **Depends on :** T13
  - **Validation :** commande CI locale.
  - **Tests liés :** tous scénarios Playwright listés.

- [ ] **T17. Docs implementation + `DEFERRED-FEATURES.md`/`TODO.md`**
  - **Outcome :** notes `docs/ai/implementation/2026-08-16-feature-elements-dossier.md` ;
    `DEFERRED-FEATURES.md` §1.4 (« Éléments de dossier » → Livré, décrire le périmètre couvert
    et les non-goals reportés) ; `TODO.md` — pas de point directement clos (rétention/purge/TODO
    §21-22 restent ouverts, à rappeler explicitement sans les rouvrir).
  - **Depends on :** T14, T15, T16
  - **Validation :** `lint --feature elements-dossier`.
  - **Tests liés :** —

## Traceability — testing → tasks

| Scénario testing | Tasks |
|---|---|
| `AddDossierElementUseCaseImpl` — 4 nouveaux types valides/invalides, non-régression `PRODUCED_DOC`, dossier introuvable, ACL | T2, T14 |
| `AddDossierElementUseCaseImpl` — garde statut `CLOTURE`/`ARCHIVE` (409, chemin `User` seulement) | T2, T14 |
| `DossierElementView.from` — `downloadable` par type | T3, T14 |
| Validation upload (fichier absent/taille/MIME/label) | T6, T9, T14 |
| `POST /{id}/elements` — JSON, 3 types, rejet `PRODUCED_DOC`/`ATTACHMENT`, ACL, 404, 409 | T5, T14 |
| `POST /{id}/elements/attachment` — succès, 413, 400 MIME, ACL, 404, 409 | T6, T7, T9, T14 |
| `GET /content` — `ATTACHMENT` généralisé, non-régression `PRODUCED_DOC`, 400 sur types non téléchargeables | T8, T14 |
| `InvalidDossierElementException` → 400 (pas de régression 500) | T7, T14 |
| `GET .../elements` — liste mixte 5 types, `downloadable` correct | T3, T5, T14 |
| Vitest `ElementsList.vue` — formulaire par type, téléchargement généralisé | T13, T15 |
| Playwright — parcours ajout pièce jointe/note/lien, erreur upload, régression `PRODUCED_DOC` | T13, T16 |
| Docs `DEFERRED-FEATURES`/`TODO` | T17 |

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2 → T5 ┐
T1 → T3 → T8 ┤
T4 ──────────┤
T9 → T6 ─────┼→ T14 → T17
T7 ──────────┘
T10 → T11 → T12 → T13 → T15 → T17
                    T13 → T16 → T17
```

- T1 bloque T2/T3/T4 (nécessaires avant tout endpoint).
- T4, T7, T9 sont indépendants entre eux et de T2/T3 — parallélisables dès le début.
- T5 dépend de T2 (validation par type) et T4 (DTO) ; T6 dépend de T2 et T9 (bornes) ; T8 dépend
  de T1/T3 (généralisation `downloadable`/type).
- Le frontend (T10–T13) peut démarrer en parallèle du backend dès T10 (correction de type
  indépendante), mais T11/T12/T13 supposent un contrat API stable — à séquencer après T4/T5/T6
  en pratique pour éviter de coder contre un contrat qui bouge, même si aucune dépendance
  technique stricte ne l'impose.
- T14 (preuves backend) après T1–T9 ; T15/T16 (preuves FE) après T10–T13 ; T17 en dernier.

## Timeline & Estimates
**When will things be done?**

| Milestone | Effort indicatif |
|---|---|
| M1 — Domaine | 0.5 j |
| M2 — API REST backend | 1 j |
| M3 — Frontend | 1 j |
| M4 — Preuves & docs | 1 j |
| **Total** | **~3.5 j** |

## Risks & Mitigation
**What could go wrong?**

| Risque | Mitigation |
|---|---|
| Dérive de typage FE déjà présente (`EXTERNAL_REF` au lieu de `LINK`, `INFORMATION` manquant) masque une régression si elle n'est pas corrigée avant d'écrire le nouveau formulaire | T10 dédié, en amont de T11-T13 |
| Fichier stocké (`store(...)` réussi) puis rejet 409 (dossier clos) ou échec `add(...)` → objet orphelin | Risque déjà accepté pour `PRODUCED_DOC` (design NFR Fiabilité) ; pas de compensation transactionnelle dans cette feature — job de purge futur (TODO §22) |
| `spring.servlet.multipart.max-file-size` mal alignée avec une borne applicative dupliquée | **Matérialisé puis éliminé en T9** — la première version du contrôleur vérifiait `file.getSize()` en plus de la config Spring, produisant 400 au lieu du 413 attendu par le design sur un fichier trop volumineux (`upload_tooLarge_returns413` a échoué en run réel : 400 reçu). Corrigé en retirant la vérification applicative — `spring.servlet.multipart.max-file-size`/`max-request-size` reste l'unique source de vérité pour la taille |
| Allowlist MIME contournable par un `Content-Type` client falsifié (le navigateur/le client HTTP décide de la valeur) | Accepté en V1 — même limitation que tout endpoint upload sans inspection du contenu (magic bytes) ; antivirus/inspection de contenu restent hors scope (TODO §30), pas à trancher silencieusement ici |
| `DossierElementUploadController` dupliquant de la logique déjà dans `AddDossierElementUseCaseImpl` (validation du `storageRef`) | La validation de forme du `storageRef` reste dans le use case (`requireValidStorageRef`, réutilisé tel quel) ; le contrôleur ne valide que ce qui est spécifique à HTTP/multipart (fichier, MIME, taille) |

## Resources Needed
**What do we need to succeed?**

- Branche `claude/next-feature-review-nk0tr3` (pas de worktree séparé — contrainte de session,
  voir note ci-dessous), JDK 25, Node/npm pour le frontend
- Skills : `tdd`, `verify`, `flowforge-conventions`
- Docs requirements/design `reviewed` (Phases 1–3 closes, y compris la garde de statut confirmée
  avec l'utilisateur) ; testing doc comme référence de scénarios

## Sequencing notes

- Ordre **layered** : domaine (T1–T3) → API backend (T4–T9, largement parallélisable) → frontend
  (T10–T13, séquentiel) → preuves (T14–T17).
- Après chaque tâche implémentée → Phase 6 (`dev-planning`, maj planning) puis reprise Phase 5
  (`dev-implementation`), TDD (`tdd` skill) sur chaque tâche à tests unitaires (T2, T3, T6, T9).
- Phase 7 (Check Implementation) quand T1–T13 `[x]`. Phase 8/9 après T14–T17.
- **Note workspace :** cette session développe directement sur la branche désignée
  `claude/next-feature-review-nk0tr3` (contrainte d'environnement CCR), pas sur un worktree
  `feature-elements-dossier` séparé comme le préconiserait `dev-worktree` par défaut — isolation
  réduite, assumé pour cette session.
