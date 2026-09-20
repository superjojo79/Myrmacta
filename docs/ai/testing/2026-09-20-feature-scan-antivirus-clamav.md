---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy — `scan-antivirus-clamav` (lot C)

> Scénarios dérivés des success criteria (Phase 1-2) et affinés contre les décisions Phase 3
> (config `app.dossier.attachment.scan.type`, métrique `flowforge.attachment.scan.fail_open`,
> timeouts 5s) — à finaliser en Phase 8 (`dev-testing`) une fois l'implémentation posée.

## Test Coverage Goals

**What level of testing do we aim for?**

- Couverture unitaire 100 % du nouveau code (`ClamAvAttachmentScanAdapter` + config), cohérent
  avec le seuil Jacoco déjà tenu sur le reste du dépôt.
- Au moins une preuve d'intégration **réelle** (pas seulement un mock du socket) contre un vrai
  `clamd`, dans l'esprit `DmnJuelSecuritySmokeIT` / `TenantSchemaIsolationIT` — isolée du run
  standard (D-AV-CLAMAV-TEST-SCOPE).
- Aucune régression sur les tests existants du chemin d'upload
  (`DossierElementUploadControllerTest`, `NoOpAttachmentScanAdapterTest`) : le comportement par
  défaut (scan désactivé) doit rester strictement identique.
- Frontend : **aucun impact** (pas de nouvel écran, pas de contrat SPA modifié) → Vitest/
  Playwright non requis pour ce lot (règle §4 `flowforge-conventions`, conditionnée à un impact
  SPA réel).

## Unit Tests

**What individual components need testing?**

### `ClamAvAttachmentScanAdapter`

- [ ] Fichier propre, `clamd` répond `stream: OK` → `AttachmentScanResult.ofClean()`
- [ ] Fichier infecté (signature test), `clamd` répond `... FOUND` → `ofInfected(reason)` avec la
      signature dans `reason`
- [ ] Connexion refusée / hôte injoignable → fail-open : pas d'exception propagée à l'appelant,
      résultat équivalent à « clean », log émis avec le motif technique
- [ ] Timeout connexion (> 5 s, D-AV-CLAMAV-TIMEOUT) → fail-open
- [ ] Timeout lecture (> 5 s, D-AV-CLAMAV-TIMEOUT) → fail-open
- [ ] Réponse protocole inattendue/malformée de `clamd` (y compris « taille de flux dépassée »,
      `StreamMaxLength`) → traitée comme un échec technique (fail-open + log), pas comme une
      infection ni un crash
- [ ] Chaque bascule fail-open (connexion refusée, timeout, réponse malformée) incrémente le
      `Counter` Micrometer `flowforge.attachment.scan.fail_open` (D-AV-CLAMAV-HEALTH) — vérifié
      via un `SimpleMeterRegistry` de test, pas seulement l'appel au logger

### Configuration / wiring (`DossierConfig`)

- [ ] `app.dossier.attachment.scan.type=noop` (défaut, absent) → bean actif reste
      `NoOpAttachmentScanAdapter`, zéro changement de comportement observable
- [ ] `app.dossier.attachment.scan.type=clamav` → bean actif devient `ClamAvAttachmentScanAdapter`
      (mêmes tests de sélection conditionnelle que `StorageConfigTest` pour
      `app.storage.type=s3`/`filesystem`, si un tel test existe déjà comme référence)

## Integration Tests

**How do we test component interactions?**

- [ ] IT réelle isolée (Testcontainers, image `clamav/clamav`, tag/profil dédié hors
      `./mvnw verify` par défaut) : upload d'un fichier de test EICAR via l'endpoint réel → rejet
      (`InvalidDossierElementException`, même famille d'erreur que les rejets existants)
- [ ] Même IT, fichier propre → upload accepté, élément créé (comportement inchangé côté
      appelant)
- [ ] Scénario panne : conteneur `clamd` arrêté/inaccessible pendant l'appel, scan activé →
      upload accepté (fail-open), log de l'incident vérifiable **et** compteur Micrometer
      `flowforge.attachment.scan.fail_open` incrémenté (D-AV-CLAMAV-HEALTH) — vérifié via
      `/actuator/prometheus` ou lecture directe du `MeterRegistry` de test
- [ ] Non-régression : `DossierElementUploadControllerTest` (scan désactivé, mock du port comme
      aujourd'hui) reste vert sans modification de ses assertions existantes

## End-to-End Tests

**What user flows need validation?**

- Aucun flux E2E navigateur nouveau à couvrir — le point d'entrée est le même endpoint REST déjà
  couvert côté FE par les tests existants d'upload de pièce jointe ; pas de nouvel écran ni de
  changement de contrat consommé par le SPA (cf. Test Coverage Goals).
- [ ] Vérifier a posteriori qu'aucun test Playwright existant lié à l'upload de pièce jointe ne
      régresse (exécution normale de la suite FE, sans modification attendue).

## Test Data

**What data do we use for testing?**

- Fichier de test standard EICAR (chaîne de test antivirus normalisée, pas un vrai virus) pour
  déclencher une détection déterministe sans risque.
- Fichiers déjà utilisés par les tests existants du chemin d'upload (PDF/PNG valides) pour le cas
  « propre ».
- Testcontainers : image `clamav/clamav:1.4` (D-AV-CLAMAV-IMAGE, Phase 3 — même tag épinglé que
  le profil Compose local/démo, pas `latest`).

## Test Reporting & Coverage

**How do we verify and communicate test results?**

- `./mvnw verify -Dmaven.compiler.release=21` (suite standard, sans l'IT ClamAV) : 0 échec,
  Jacoco maintenu au seuil actuel, `ClamAvAttachmentScanAdapter` visé à 100 % en couverture
  unitaire (mocks du socket).
- IT ClamAV dédiée exécutée séparément : `./mvnw test -Dtest=ClamAvAttachmentScanIT`
  (D-AV-CLAMAV-TEST-SCOPE) ; job CI séparé à évaluer en Phase 4/5 selon le coût réel mesuré du
  démarrage `clamd` (~1-2 min).

## Manual Testing

**What requires human validation?**

- Démarrage réel `docker compose --profile clamav up -d` + upload manuel d'un fichier EICAR via
  l'UI existante, pour valider le chemin complet en conditions proches de la prod (pas seulement
  Testcontainers).
- Vérification que `docker compose up -d` **sans** le profil `clamav` (comportement par défaut)
  ne casse rien — `clamd` n'est pas requis tant que le scan n'est pas activé.

## Performance Testing

**How do we validate performance?**

- Pas de campagne de charge dédiée pour ce lot (hors scope, cohérent avec le reste du dépôt qui
  ne fait pas de load testing formel en V1) — seule vérification : la latence ajoutée par un scan
  `clamd` réel reste raisonnable sur un fichier de taille max autorisée, mesurée manuellement
  pendant l'implémentation (pas un critère de succès chiffré bloquant à ce stade).

## Bug Tracking

**How do we manage issues?**

- Même processus que le reste du dépôt : écarts réels trouvés en cours de cycle consignés dans
  `TODO.md` / le document d'implémentation de la feature, pas de tracker externe dédié.
