---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-08-16
updated: 2026-08-17
feature: elements-dossier
phase7: 2026-08-17
---

# Implementation Guide — `elements-dossier`

## Development Setup

Même stack que le reste du module `dossier` (Java 25, Spring Boot 4.1, H2
mode PostgreSQL pour les tests backend ; Node/npm + Vitest/Playwright côté
frontend). Développé directement sur la branche désignée de session
`claude/next-feature-review-nk0tr3` — pas de worktree `feature-elements-dossier`
séparé (contrainte d'environnement CCR, voir note dans le planning doc).

## Code Structure

Aucun nouveau package. Fichiers créés/modifiés :

**Backend**

- `domain/dossier/model/ElementType.java` — 4 nouvelles valeurs.
- `domain/dossier/model/DossierElementView.java` — `downloadable` généralisé.
- `domain/dossier/usecase/AddDossierElementUseCaseImpl.java` — validation
  par type + garde de statut.
- `infrastructure/config/AttachmentUploadProperties.java` (nouveau) —
  allowlist MIME configurable.
- `infrastructure/config/DossierConfig.java` — `@EnableConfigurationProperties(AttachmentUploadProperties.class)`.
- `infrastructure/web/dossier/DossierApiDtos.java` — nouveau record
  `AddDossierElementRequest`.
- `infrastructure/web/dossier/DossierController.java` — nouvelle méthode
  `POST /{id}/elements`.
- `infrastructure/web/dossier/DossierElementUploadController.java`
  (nouveau) — `POST /{id}/elements/attachment`, multipart.
- `infrastructure/web/dossier/DossierExceptionHandler.java` — mappings
  `InvalidDossierElementException` → 400, `MaxUploadSizeExceededException`
  → 413.
- `infrastructure/web/document/DossierElementContentController.java` —
  généralisation `Content-Type`/nom de fichier pour `ATTACHMENT`.
- `application.yml` — `spring.servlet.multipart.*` + `resolve-lazily` +
  `app.dossier.attachment.allowed-content-types`.

**Frontend**

- `frontend/src/api/types.ts` — `ElementType` corrigé (dérive préexistante).
- `frontend/src/api/dossiers.ts` — `addDossierElement`.
- `frontend/src/api/elements.ts` — `uploadAttachment`.
- `frontend/src/stores/dossier.ts` — actions `addElement`/`addAttachment`.
- `frontend/src/components/dossier/ElementsList.vue` — slot `#actions`,
  affichage `NOTE`/`LINK`, téléchargement généralisé.
- `frontend/src/components/dossier/AddElementDialog.vue` (nouveau).
- `frontend/src/views/pilotage/DossierDetailView.vue` — wiring du dialogue.
- `frontend/playwright.config.ts` — `launchOptions.executablePath`
  optionnel via `PLAYWRIGHT_CHROMIUM_EXECUTABLE` (contrainte
  d'environnement, voir Deviations).

## Implementation Notes

### Core Features

- **Validation par type (`AddDossierElementUseCaseImpl.validateForType`) :**
  un `switch` exhaustif sur `ElementType` — `PRODUCED_DOC`/`ATTACHMENT`
  réutilisent `requireValidStorageRef` (message généralisé, ne mentionne
  plus `PRODUCED_DOC` en dur) ; `INFORMATION` exige `metadata` non vide ;
  `NOTE` exige `metadata.text` non blanc ; `LINK` exige `metadata.url`
  résolu en `URI` absolue (`new URI(...).isAbsolute()`), jamais fetchée.
- **Garde de statut (D-STATUS-GATE, scope humain) :** `ctx instanceof
  AccessContext.User && BLOCKED_STATUSES.contains(dossier.status())` →
  `DossierConflictException` (409), vérifiée **après** `assertCanEdit`
  (même ordre que `UpdateDossierFieldsUseCaseImpl`). `PRODUCED_DOC`
  (toujours `AccessContext.System`) n'est jamais concerné par construction
  — pas de condition explicite sur le type.
- **ACL avant stockage (`DossierElementUploadController`) :** trouvaille
  d'une session parallèle sur la même feature (voir Deviations) —
  `DossierAccessGuard.requireEditable(id, actor)` appelé avant
  `DocumentStoragePort.store(...)`, pour qu'un utilisateur sans `EDIT` ne
  cause jamais l'écriture d'un fichier avant son rejet en 403.
  `AddDossierElementUseCase` revalide la même ACL en interne — vérifié en
  Phase 9 que ce n'est **pas** gratuit comme d'abord documenté :
  `AddDossierElementUseCaseImpl.add()` refait son propre `findById`, et
  `DossierAccessPolicy.assertCanEdit` interroge elle-même
  `DossierTypeAclRepository` (sauf acteur `TENANT_ADMIN`/`WORKFLOW_ADMIN`).
  Coût réel : un `findById` + une requête ACL indexée en plus par upload,
  pas un vrai second I/O de fichier — accepté, même politique/règle des
  deux côtés, pas une seconde source de vérité.
- **Taille d'upload — une seule source de vérité :** `spring.servlet
  .multipart.max-file-size`/`max-request-size` (+ `resolve-lazily: true`,
  nécessaire pour que `MaxUploadSizeExceededException` remonte depuis
  l'intérieur du contrôleur plutôt que pendant le dispatch). Aucune
  vérification de taille applicative dans le contrôleur — une première
  version dupliquait ce contrôle dans `AttachmentUploadProperties
  .maxSizeBytes` et produisait 400 au lieu de 413 (voir Deviations).
- **`DossierElementContentController` généralisé :** `PRODUCED_DOC`
  inchangé (`application/pdf`, `<label>.pdf` en dur) ; `ATTACHMENT` lit
  `metadata.contentType`/`metadata.filename` avec fallback
  `application/octet-stream`/label si absents.

### Patterns & Best Practices

- Même patron que `AddDossierElementUseCaseImpl` existant pour la
  structure (charge dossier → tenant check → ACL → construit l'entité) ;
  `switch` exhaustif Java 21 pour la validation par type, cohérent avec
  `WorkflowEvent` (décision n°6 du TODO).
- Frontend : `ElementsList.vue`/`AddElementDialog.vue` répliquent
  exactement le patron déjà établi par `RelationsList.vue`/
  `LinkDossierDialog.vue` (slot `#actions`, dialogue autonome avec
  `open`/`close`/`submit*`, réinitialisation au `watch(() => props.open)`).
  `stores/dossier.ts` ajoute au tableau en mémoire (`elements.value =
  [...elements.value, created]`) plutôt que de refetch, même patron que
  `linkDossier`.

## Integration Points

- `POST /api/dossiers/{id}/elements` (JSON) — `INFORMATION`/`NOTE`/`LINK`.
- `POST /api/dossiers/{id}/elements/attachment` (multipart) — `ATTACHMENT`.
- `GET /api/dossier-elements/{id}/content` (existant, généralisé).
- Aucune nouvelle table, aucune migration Liquibase (`dossier_element.type`
  déjà `varchar(32)` sans `CHECK`, vérifié en Phase 1).
- Réutilisation stricte de `DocumentStoragePort`, `DossierAccessPolicy`,
  `DossierAccessGuard` — aucun nouveau port.

## Error Handling

| Cas | HTTP | Exception |
|---|---|---|
| `type` manquant/`PRODUCED_DOC`/`ATTACHMENT` sur `POST .../elements` | 400 | `IllegalArgumentException` |
| Validation par type invalide (storageRef/metadata/URL) | 400 | `InvalidDossierElementException` |
| Fichier absent, MIME hors allowlist | 400 | `InvalidDossierElementException` |
| Fichier trop volumineux (conteneur) | 413 | `MaxUploadSizeExceededException` |
| Dossier introuvable / autre tenant | 404 | `DossierNotFoundException` |
| Pas de permission `EDIT` | 403 | `AccessDeniedException` |
| Statut `CLOTURE`/`ARCHIVE` (chemin humain) | 409 | `DossierConflictException` |
| `GET .../content` sur élément sans contenu téléchargeable | 409 | `InvalidDossierElementException` (mapping **existant**, `DocumentGenerationExceptionHandler`, différent du 400 posé pour les 2 nouveaux endpoints d'ajout — deux `@RestControllerAdvice` à portée disjointe, pas une incohérence) |

## Performance Considerations

Upload synchrone, taille bornée par la config Spring (25 Mo par défaut).
Pas de N+1 : `AddDossierElementUseCaseImpl` fait un `findById` + un
`save()`, comme l'existant. `DossierElementUploadController` ajoute un
`requireEditable` (un `findById` + ACL) avant le `store()` — un aller-retour
DB de plus qu'avant l'ajout de la garde ACL-avant-storage, jugé négligeable
face au coût de l'I/O de stockage qu'il évite en cas de rejet.

## Security Notes

- ACL réutilisée sans extension (`DossierAccessPolicy`/`DossierAccessGuard`
  existants) — `assertCanEdit` pour les deux endpoints d'ajout,
  `assertCanView` pour lecture/liste/téléchargement.
- `LINK` jamais résolu côté serveur — aucune nouvelle surface SSRF (pas de
  `RestrictedResourceUserAgent`/`OutboundUrlGuard` à étendre).
- Allowlist MIME configurable (`AttachmentUploadProperties`), pas figée en
  dur — un `Content-Type` client reste déclaratif (non vérifié par
  inspection du contenu/magic bytes), limitation acceptée en V1, cohérente
  avec l'absence d'antivirus (TODO §30, non traité ici).
- Stockage avant ACL fermé (voir Core Features) — élimine le vecteur
  "upload de fichiers volumineux par un utilisateur sans droit, à
  répétition, pour consommer du stockage".

## Deviations from Design

**Écart matériel corrigé en cours d'implémentation — bornes de taille
dupliquées.** Le design (Component Breakdown) prévoyait `AttachmentUploadProperties`
avec `maxSizeBytes` **et** `allowedContentTypes`. Une première
implémentation vérifiait `file.getSize() > uploadProperties.maxSizeBytes()`
dans le contrôleur, en plus de `spring.servlet.multipart.max-file-size`.
Un test réel (`upload_tooLarge_returns413`) a immédiatement révélé le
problème anticipé dans le planning doc (table Risks, T9) : la vérification
applicative se déclenchait avant que Spring n'ait la chance de rejeter la
requête, produisant 400 au lieu du 413 attendu. Corrigé en retirant
`maxSizeBytes`/la vérification applicative — `spring.servlet.multipart.*`
reste l'unique source de vérité pour la taille (voir Core Features).

**Convergence avec une session parallèle sur la même feature.** Pendant
l'exécution de cette Phase 5, une autre session (même identifiant de
session sous-jacent, invocation concurrente) a poussé indépendamment sur
la même branche distante une résolution de la Phase 2 (garde de statut)
identique en substance à celle déjà actée ici, plus une trouvaille propre :
l'ACL doit être vérifiée avant l'écriture en storage dans
`DossierElementUploadController`, pas seulement dans le use case appelé
après. Cette trouvaille a été reprise (voir Core Features, "ACL avant
stockage") après fusion des deux historiques git (`git merge`, conflits
résolus dans les 3 docs `requirements`/`design`/`testing` en conservant la
formulation la plus détaillée de chaque côté).

**Écart d'environnement, sans rapport avec la feature.** `playwright.config.ts`
gagne un `launchOptions.executablePath` conditionnel
(`PLAYWRIGHT_CHROMIUM_EXECUTABLE`) pour pointer vers le Chromium
pré-installé de ce bac à sable (revision différente de celle que
`@playwright/test` tente de télécharger par défaut). Opt-in par variable
d'environnement, aucun effet sur CI ou les autres postes de développement
tant que la variable n'est pas positionnée.

## Phase 7 — Check Implementation (revue fichier par fichier)

**Méthode :** chaque fichier livré relu contre le design (composants clés,
décisions n°1–6, Component Breakdown) et contre les requirements (success
criteria, edge cases, non-goals).

| Fichier | Intention design | Statut |
|---|---|---|
| `ElementType.java` | 4 valeurs ajoutées, `PRODUCED_DOC` inchangé | Conforme |
| `DossierElementView.java` | `downloadable` généralisé à `ATTACHMENT` | Conforme — testé (`DossierElementViewTest`, 5 cas) |
| `AddDossierElementUseCaseImpl.java` | validation par type + garde de statut sur chemin humain uniquement | Conforme — 16 tests unitaires, y compris non-régression `PRODUCED_DOC` sur dossier clos |
| `AttachmentUploadProperties.java` | allowlist MIME configurable | Conforme (après retrait de `maxSizeBytes`, voir Deviations) |
| `DossierController.java` (`addElement`) | rejette `PRODUCED_DOC`/`ATTACHMENT`, délègue au use case | Conforme — MockMvc 9 scénarios (201/400/403/404/409) |
| `DossierElementUploadController.java` | ACL avant storage → store → use case | Conforme — MockMvc + test unitaire dédié sur l'ordre ACL/storage |
| `DossierExceptionHandler.java` | +2 mappings | Conforme — testé unitairement et par MockMvc (400) |
| `DossierElementContentController.java` | généralisation `Content-Type`/filename | Conforme — non-régression `PRODUCED_DOC` + nouveau cas `ATTACHMENT` testés |
| Frontend (`ElementsList`, `AddElementDialog`, store, vue) | slot actions, formulaire par type, actions store sans reload | Conforme — Vitest (16 tests) + Playwright (6 scénarios e2e) |

**Trous de logique / cas limites :** aucun trouvé qui ne soit déjà couvert
par un test. Point vérifié spécifiquement : le 413 sur upload volumineux
n'est testable ni en MockMvc ni en Playwright avec des moyens réalistes
dans ce projet (limitation de l'outillage de test, pas du code) — compensé
par un test unitaire direct sur le mapping et un test e2e qui mocke la
réponse HTTP 413 pour vérifier le traitement client.

**Sécurité :** ACL réutilisée + durcie (ACL avant storage) ; pas de
nouvelle surface SSRF (`LINK` jamais résolu) ; isolation tenant inchangée
(`AddDossierElementUseCaseImpl` compare `tenantId` chargé depuis le
repository, jamais depuis l'entrée cliente).

**Docs/tests manquants :** aucun. Testing doc entièrement coché (avec
annotations sur les 2 scénarios non couvrables directement en MockMvc/e2e
réel) ; `DEFERRED-FEATURES.md`/`TODO.md` restent à mettre à jour (T17,
juste après cette section).

## Alignment Summary

**Statut :** aligné, aucune déviation majeure — les deux écarts relevés
(bornes de taille dupliquées, convergence avec la session parallèle) ont
été détectés et corrigés pendant l'implémentation elle-même, pas laissés
en dette. Prêt pour Phase 9 (`dev-review`).

## Phase 9 — Revue de code finale (holistique)

**Méthode :** relecture fraîche des fichiers de production (pas seulement
des diffs), traçage des appelants pour les signatures changées
(`DossierController` constructeur — aucune instanciation manuelle en test,
uniquement via le contexte Spring), recherche de motifs `console.log`/
`System.out.println`/`TODO`/`FIXME`/`debugger` sur tout le diff (aucun),
comparaison avec `DossierElementContentApiTest`/`DossierApiTest` pour la
cohérence des patrons de test, vérification qu'`ElementsList` n'a qu'un
seul appelant (`DossierDetailView.vue`).

**Trouvé et corrigé (bloquant, sécurité) :** `AddDossierElementUseCaseImpl
.isAbsoluteUri` acceptait n'importe quel schéma d'URI absolue pour `LINK`,
y compris `javascript:`. `ElementsList.vue` rend `LINK.metadata.url` en
`<a href>` cliquable (`target="_blank"`) — un acteur disposant de `EDIT`
sur le type de dossier aurait pu stocker un `LINK` dont le clic exécute du
JavaScript dans la session d'un autre utilisateur consultant le même
dossier (stored-XSS via un vecteur non-HTML). Aucune interpolation
`v-html` n'est utilisée nulle part dans le nouveau code frontend (vérifié
par grep) — le seul vecteur restant était le schéma d'URI. Corrigé en
restreignant `isAbsoluteUri` aux schémas `http`/`https` ; test dédié
ajouté (`add_linkWithJavascriptScheme_throws`). N'affecte pas le non-goal
"aucune résolution serveur de `LINK`" — c'est une validation de forme,
pas une résolution.

**Trouvé et corrigé (mineur, exactitude de la documentation) :** le
commentaire de code et l'implementation doc affirmaient que la
revalidation ACL dans `AddDossierElementUseCaseImpl` (après le
`DossierAccessGuard.requireEditable` du contrôleur) était [1]"bon marché,
en mémoire". Vérifié faux par lecture de `DossierAccessPolicy.assertCanEdit`
: elle interroge `DossierTypeAclRepository` (sauf acteur
`TENANT_ADMIN`/`WORKFLOW_ADMIN`), et `AddDossierElementUseCaseImpl.add()`
refait son propre `findById`. Corrigé aux deux endroits (commentaire Java
+ § Implementation Notes) pour décrire le coût réel : un `findById` + une
requête ACL indexée en plus par upload — accepté, mais plus précisément
documenté.

**Observations non-bloquantes (pas introduites par cette feature) :**
- `INFORMATION` côté frontend n'accepte qu'une seule paire clé/valeur par
  ajout (`AddElementDialog.vue`), alors que le concept documenté est un
  "bloc de données structurées" plus général. Le design ne spécifiait pas
  de cardinalité, et aucun scénario de testing doc n'exige plusieurs
  paires — traité comme une simplification acceptable de V1, pas un écart
  de scope. Le backend, lui, accepte déjà n'importe quel `Map<String,Object>`
  non vide ; un futur besoin multi-paires n'exigerait qu'un changement
  frontend.
- `DossierController` a maintenant 11 dépendances injectées au
  constructeur (10 avant cette feature) — tendance déjà relevée comme
  dette préexistante par la feature précédente (`patch-dossier-fields`),
  accentuée mais pas causée ici.

**Sécurité (bilan) :** ACL réutilisée + durcie par l'ordre ACL-avant-storage
sur l'endpoint d'upload ; aucune nouvelle surface SSRF (`LINK` jamais
résolu serveur) ; schéma d'URI restreint http(s) (trouvaille ci-dessus,
corrigée) ; aucune fuite de `storageRef` dans les réponses API
(`DossierElementView` ne l'expose jamais, comportement hérité inchangé) ;
isolation tenant inchangée.

**Checklist finale :**
- [x] Correspondance avec le design — vérifiée fichier par fichier
      (Phase 7), un écart de sécurité trouvé et corrigé ici (Phase 9), pas
      un écart de design.
- [x] Aucun trou de logique bloquant restant.
- [x] Sécurité traitée (ACL durcie, pas de nouvelle surface SSRF, schéma
      d'URI restreint, isolation tenant).
- [x] Points d'intégration vérifiés (wiring Spring, routes existantes non
      affectées, `ElementsList` à appelant unique).
- [x] Tests couvrent les changements (729 backend + 87 frontend Vitest +
      20 Playwright, tous verts après le correctif de sécurité).
- [x] Docs à jour (les 7 docs `ai-devkit`, `DEFERRED-FEATURES.md`,
      `TODO.md`).

**Verdict :** prête à pousser. Aucune PR créée — en attente d'une demande
explicite de l'utilisateur.

[1]: Formulation initialement reprise telle quelle d'un message de commit
d'une session parallèle sur la même feature, sans revérification contre le
code réel — corrigée ici après vérification.
