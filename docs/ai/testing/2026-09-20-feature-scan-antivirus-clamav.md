---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy — `scan-antivirus-clamav` (lot C)

> Phase 5 (implémentation) exécutée : scénarios cochés au fur et à mesure, quelques ajouts
> réels trouvés en cours de route (branches non prévues au brouillon). Finalisation formelle en
> Phase 8 (`dev-testing`).

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

- [x] Fichier propre, `clamd` répond `stream: OK` → `AttachmentScanResult.ofClean()`
- [x] Fichier infecté (signature test), `clamd` répond `... FOUND` → `ofInfected(reason)` avec la
      signature dans `reason`
- [x] Connexion refusée / hôte injoignable → fail-open : pas d'exception propagée à l'appelant,
      résultat équivalent à « clean », log émis avec le motif technique
- [x] Timeout connexion (> 5 s, D-AV-CLAMAV-TIMEOUT) → fail-open (test avec une adresse
      TEST-NET-1 non routée, RFC 5737, pour un vrai timeout et pas un refus immédiat)
- [x] Timeout lecture (> 5 s, D-AV-CLAMAV-TIMEOUT) → fail-open
- [x] Réponse protocole inattendue/malformée de `clamd` (y compris « taille de flux dépassée »,
      `StreamMaxLength`) → traitée comme un échec technique (fail-open + log), pas comme une
      infection ni un crash
- [x] Chaque bascule fail-open (connexion refusée, timeout, réponse malformée) incrémente le
      `Counter` Micrometer `flowforge.attachment.scan.fail_open` (D-AV-CLAMAV-HEALTH) — vérifié
      via un `SimpleMeterRegistry` de test, pas seulement l'appel au logger
- [x] Contenu vide (`byte[0]`) → terminateur `INSTREAM` envoyé directement, réponse `clean`
- [x] **Trouvé en visant 100 % de couverture branche (Jacoco), pas anticipé au brouillon** :
      réponse commençant par `stream: ` mais ne se terminant pas par ` FOUND` (statut clamd non
      reconnu) → fail-open, pas confondu avec un verdict infecté
- [x] **Idem** : connexion fermée par `clamd` juste après la réponse, sans octet NUL terminal —
      `readResponse()` doit s'arrêter sur la fin de flux (-1), pas seulement sur le NUL
- [x] **Trouvé en Phase 7 (check implementation)** : réponse dépassant `MAX_RESPONSE_BYTES`
      (4096, jamais bufferisée indéfiniment — même convention que
      `ConnectorProperties.Http.maxResponseBytes`) → fail-open, pas un buffer illimité

### Configuration / wiring (`DossierConfig`)

- [x] `app.dossier.attachment.scan.type=noop` (défaut, absent) → bean actif reste
      `NoOpAttachmentScanAdapter`, zéro changement de comportement observable
- [x] `app.dossier.attachment.scan.type=clamav` → bean actif devient `ClamAvAttachmentScanAdapter`
      (même technique d'instanciation directe que `StorageConfigTest`)

## Integration Tests

**How do we test component interactions?**

**Périmètre révisé en Phase 5** : `ClamAvAttachmentScanIT` teste l'adaptateur directement contre
un vrai conteneur `clamd` (Testcontainers), pas via l'endpoint HTTP complet — même principe que
`DmnJuelSecuritySmokeIT` (« ne prouve pas que le chemin HTTP complet fonctionne — déjà couvert,
mocké, par `DossierElementUploadControllerTest` — prouve que le protocole implémenté à la main
est effectivement compris par un vrai moteur »). Le contrôleur est frozen (diff nul imposé) et
déjà entièrement couvert côté mock ; ajouter dossier/auth/ACL pour un aller-retour HTTP complet
n'aurait apporté aucune preuve supplémentaire sur le seul point réellement à risque : le
protocole `INSTREAM` fait main.

- [x] IT réelle isolée (Testcontainers, image `clamav/clamav:1.4`, nommage `*IT.java` seul —
      D-AV-CLAMAV-TEST-SCOPE) : fichier EICAR → `AttachmentScanResult.ofInfected(...)`
- [x] Même IT, fichier propre → `ofClean()`
- [x] Scénario panne : conteneur `clamd` arrêté (`.stop()`) entre deux appels → deuxième appel
      accepté (fail-open), compteur Micrometer `flowforge.attachment.scan.fail_open` incrémenté
      (D-AV-CLAMAV-HEALTH)
- [x] Non-régression : `DossierElementUploadControllerTest` (scan désactivé, mock du port comme
      aujourd'hui) reste vert sans modification de ses assertions existantes — confirmé par
      `./mvnw verify` complet (1981 tests, 0 échec)
- **Non exécuté dans ce bac à sable** (démon Docker absent — confirmé, pas supposé : `docker
  pull` échoue avec « failed to connect to the docker API »). Écrit, compilé
  (`./mvnw test-compile` vert) et exécuté explicitement une fois : les deux tests passent en
  `SKIPPED` proprement via `Assumptions.assumeTrue(DockerClientFactory...)`, même garde que
  `TenantSchemaIsolationIT`/`SequentialReferenceMultiPodIT` — à exécuter réellement en CI ou sur
  un poste avec Docker actif avant de considérer ce lot totalement clos.

## End-to-End Tests

**What user flows need validation?**

- Aucun flux E2E navigateur nouveau à couvrir — le point d'entrée est le même endpoint REST déjà
  couvert côté FE par les tests existants d'upload de pièce jointe ; pas de nouvel écran ni de
  changement de contrat consommé par le SPA (cf. Test Coverage Goals).
- [ ] Vérifier a posteriori qu'aucun test Playwright existant lié à l'upload de pièce jointe ne
      régresse (exécution normale de la suite FE, sans modification attendue) — reporté à la
      Phase 8 (`dev-testing`), aucun fichier frontend touché en Phase 5.

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

- `./mvnw verify -Dmaven.compiler.release=21 -Dskip.frontend=true` (suite standard, sans l'IT
  ClamAV) : **1981 tests, 0 échec**, seuils Jacoco globaux tenus (LINE ≥85%/BRANCH ≥75%, `pom.xml`
  inchangés), `ClamAvAttachmentScanAdapter` à **100% instruction/ligne/branche** (vérifié via
  `target/site/jacoco/jacoco.csv` et `jacoco.xml`, pas seulement affirmé).
- IT ClamAV dédiée exécutée séparément : `./mvnw test -Dtest=ClamAvAttachmentScanIT`
  (D-AV-CLAMAV-TEST-SCOPE, confirmé absent du run précédent — 0 occurrence du nom de la classe
  dans les logs du `./mvnw verify` standard). Job CI séparé toujours à évaluer une fois le temps
  de démarrage `clamd` mesuré réellement (impossible dans ce bac à sable, Docker absent).

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
