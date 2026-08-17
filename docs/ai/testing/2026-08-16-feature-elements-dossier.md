---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy — `elements-dossier`

## Test Coverage Goals

- Unit/domain : couverture des 4 nouveaux types dans `AddDossierElementUseCaseImpl`, calcul
  `downloadable` généralisé, validation MIME/taille dans le contrôleur d'upload.
- Intégration (`@SpringBootTest` / `MockMvc`, pattern existant `DossierApiTest`,
  `DossierElementContentApiTest`) : les 2 nouveaux endpoints REST + généralisation du endpoint de
  contenu, ACL, tenant isolation.
- E2E Playwright : parcours complet ajout pièce jointe + note depuis l'UI, alignement avec
  `flowforge-conventions` (Vitest **et** Playwright obligatoires, SPA + contrat API touchés).
- Aligné sur les critères de succès du [requirements doc](../requirements/2026-08-16-feature-elements-dossier.md)
  et les endpoints du [design doc](../design/2026-08-16-feature-elements-dossier.md).

## Unit Tests

### `AddDossierElementUseCaseImpl` (`AddDossierElementUseCaseImplTest`, existant à étendre)
- [ ] `ATTACHMENT` valide (storageRef tenant-préfixé + label) → élément créé
- [ ] `ATTACHMENT` sans `storageRef` → `InvalidDossierElementException`
- [ ] `INFORMATION` valide (metadata non vide) → élément créé, `storageRef` null
- [ ] `NOTE` valide (`metadata.text` non vide) → élément créé, `storageRef` null
- [ ] `NOTE` avec `metadata.text` vide/absent → `InvalidDossierElementException`
- [ ] `LINK` valide (`metadata.url` bien formée) → élément créé, `storageRef` null
- [ ] `LINK` avec URL malformée → `InvalidDossierElementException`
- [ ] `PRODUCED_DOC` — comportement existant inchangé (non-régression, ne pas dupliquer les tests
      déjà présents, juste vérifier absence de régression sur la branche existante)
- [ ] Dossier introuvable / autre tenant → `DossierNotFoundException` (même comportement pour les
      4 nouveaux types que pour `PRODUCED_DOC`)
- [ ] Acteur sans ACL `EDIT` sur le type de dossier → `AccessDeniedException` (chacun des 4 types)

### `DossierElementView.from(...)` (extension du test existant)
- [ ] `downloadable=true` pour `ATTACHMENT` avec `storageRef` renseigné
- [ ] `downloadable=false` pour `INFORMATION`/`NOTE`/`LINK` (pas de `storageRef`)
- [ ] `downloadable` inchangé pour `PRODUCED_DOC` (non-régression)

### Validation upload (nouveau — composant à isoler, ex. `AttachmentUploadValidator` ou logique
inline testée via le contrôleur)
- [ ] Fichier absent → rejet
- [ ] Fichier au-dessus de la taille max → rejet (413/400)
- [ ] Type MIME hors allowlist → rejet (400)
- [ ] `label` absent/vide → rejet (400)
- [ ] Fichier valide, type MIME autorisé, taille sous la limite → accepté

## Integration Tests

- [ ] `POST /api/dossiers/{id}/elements` (JSON) — `NOTE`/`INFORMATION`/`LINK` créés avec succès,
      201 + body `DossierElementView` conforme
- [ ] `POST /api/dossiers/{id}/elements` avec `type=PRODUCED_DOC` ou `type=ATTACHMENT` → 400
      (orientation vers les endpoints dédiés)
- [ ] `POST /api/dossiers/{id}/elements/attachment` (multipart) — upload réussi, `storageRef`
      renseigné, `metadata.filename`/`contentType`/`sizeBytes` corrects
- [ ] `POST /api/dossiers/{id}/elements/attachment` — fichier trop volumineux → 413 avec
      `ProblemDetail`
- [ ] `POST /api/dossiers/{id}/elements/attachment` — type MIME refusé → 400 avec `ProblemDetail`
- [ ] Les deux endpoints d'ajout — utilisateur avec seulement `VIEW` sur le type → 403
- [ ] Les deux endpoints d'ajout — dossier d'un autre tenant → 404 (pas de fuite d'existence
      cross-tenant)
- [ ] `GET /api/dossier-elements/{id}/content` sur un `ATTACHMENT` → 200, `Content-Type` et
      `Content-Disposition` reflétant le fichier original
- [ ] `GET /api/dossier-elements/{id}/content` sur un `PRODUCED_DOC` → **non-régression**, toujours
      `application/pdf`
- [ ] `GET /api/dossier-elements/{id}/content` sur `INFORMATION`/`NOTE`/`LINK` →
      `InvalidDossierElementException` → 400 ("no downloadable content", comportement déjà
      existant pour un élément sans `storageRef`)
- [ ] `GET /api/dossiers/{id}/elements` — liste mixte des 5 types, `downloadable` correct pour
      chacun
- [ ] `InvalidDossierElementException` levée depuis `DossierController`/
      `DossierElementUploadController` → 400 via `DossierExceptionHandler` (mapping actuellement
      absent pour le package `web.dossier`, à ajouter — couvrir par un test dédié pour éviter une
      régression silencieuse vers un 500)

## End-to-End Tests (Playwright)

- [ ] Utilisateur `DOSSIER_MANAGER` : ouvre un dossier, clique "Ajouter", choisit "Pièce jointe",
      sélectionne un fichier, valide → l'élément apparaît dans la liste, téléchargeable
- [ ] Utilisateur `DOSSIER_MANAGER` : ajoute une "Note" texte libre → apparaît dans la liste, non
      téléchargeable (pas de lien de téléchargement affiché)
- [ ] Utilisateur `DOSSIER_MANAGER` : ajoute un "Lien" avec une URL → apparaît dans la liste avec
      l'URL affichée (pas de fetch déclenché par le navigateur non plus, simple affichage)
- [ ] Tentative d'upload d'un fichier trop volumineux depuis l'UI → message d'erreur visible, pas
      de crash
- [ ] Utilisateur `DOSSIER_VIEWER` : bouton "Ajouter" absent ou désactivé (ACL en lecture seule) —
      à vérifier une fois le comportement UI précisé en implémentation
- [ ] Régression : téléchargement d'un `PRODUCED_DOC` existant toujours fonctionnel après le
      changement du contrôleur de contenu

## Test Data

- Fixtures : réutiliser les builders `DossierElement`/`AccessContext` déjà présents dans les
  tests existants (`AddDossierElementUseCaseImplTest`, `DossierApiTest`).
- Fichiers de test upload : petit PDF/PNG valides en ressources de test (`src/test/resources`),
  plus un fichier > taille max généré en mémoire pour le cas de rejet.
- Pas de nouvelle base de données/schéma de test — H2 MODE=PostgreSQL existant suffit (aucune
  migration Liquibase requise, cf. design).

## Test Reporting & Coverage

- `./mvnw verify` (Jacoco, seuil existant du projet) + ArchUnit (aucune nouvelle violation de
  frontière domaine/infrastructure attendue).
- `cd frontend && npm run test -- --coverage` pour les composants FE touchés.
- `cd frontend && npm run test:e2e` (ou `test:all`) obligatoire avant de clore la Phase 8/9
  (rappel `flowforge-conventions` : interdit de déclarer Playwright hors scope ici, le SPA et son
  contrat API sont directement impactés).

## Manual Testing

- Vérification visuelle du formulaire "Ajouter" par type dans PrimeVue (cohérence avec la
  maquette UI de référence).
- Vérification qu'un `LINK` affiché ne déclenche aucune requête sortante automatique depuis le
  navigateur ou le serveur (contrôle SSRF négatif).

## Performance Testing

- Pas de campagne dédiée — hors scope pour ce lot (upload synchrone, taille bornée à 25 Mo par
  défaut, cf. design NFR).

## Bug Tracking

- Suivi standard du dépôt (issues/PR) ; toute régression détectée sur `PRODUCED_DOC` pendant
  cette feature est bloquante (non-goal explicite de casser l'existant).
