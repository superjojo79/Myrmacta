# Outillage de démo et de capture pour le site — document de travail

Issue : #55 (vague `vague-vitrine`, avec #56 à #59) · Spec du domaine : docs/specs/47-postgresql-embarque-tests-demo.md ·
Points du registre : à créer en conception (§127 pressenti)

## Cadrage

- **Problème et utilisateurs :** le site myrmacta.be a besoin de capsules vidéo et de screenshots. Les produire à la
  main ne tient pas : l'UI change à chaque release. Utilisateurs : le mainteneur, qui régénère les captures, et
  quiconque prépare une démo commerciale.
- **Périmètre :**
  - un profil Spring `demo`, composé de **paquets** (un seeder par exemple), dans le build `embedded-db` seulement :
    mail room (le `MailRoomDemoSeeder` actuel devient un paquet ; `demo-mailroom` reste utilisable seul), onboarding
    collaborateur, et un paquet « états » (dossiers à différents stades, SLA dépassés, un
    incident rejouable, un brouillon MCP) ;
  - des **faux SIRH, IAM et ITSM** embarqués dans le profil de démo, qui répondent avec succès, pour qu'un onboarding
    traverse le vrai processus et remplisse ses champs « Provisioning » ;
  - des **données en anglais** : une langue par démarrage (`APP_DEMO_LOCALE=fr|en`), le seeder lisant une variante
    traduite des fichiers d'exemple ; les fichiers français restent la source ;
  - un **cadre de capture Playwright** à part (`frontend/capture/`, `playwright.capture.config.ts`,
    `npm run capture`), qui pilote une vraie instance de démo, connexion Keycloak locale `demo` / `demo` ;
  - les **14 screenshots** de #58 (S1 à S15 sauf S13, portail usager), en FR et en EN ;
  - la documentation : un guide de démo et de capture, la spec 47, la configuration de l'IA locale validée
    (Ollama, `gemma4:12b`, `reasoning-effort=none`).
- **Hors périmètre :**
  - l'exemple autorisation riverain, retiré par l'utilisateur le 2026-11-11 : pas de paquet riverain, donc pas de
    screenshot S13 (portail usager) ; les capsules C5 (portail) et C6 (pré-vérification IA) de #58 n'ont plus de
    données dans la démo ;
  - les scripts et montages des capsules vidéo (#56, #57, #58) et du teaser (#59) ;
  - les réponses en erreur des faux services, pour la partie « robustesse » de L2 (#57) ;
  - toute modification de l'assistant de conception (câblé sur Anthropic) ;
  - toute exécution en CI : les captures se lancent à la main, contre une instance locale.
- **Critères de succès (vérifiables) :**
  1. sur une base vide, `scripts/demo.sh start` installe les deux exemples (mail room, onboarding) et les états, en FR par défaut ;
  2. avec `APP_DEMO_LOCALE=en`, les mêmes données apparaissent en anglais (libellés de types et de champs, statuts,
     questionnaires, gabarits, modèles de documents, sorties de la table DMN visibles à l'écran) ;
  3. l'onboarding semé atteint « Provisionné » par le vrai processus, champs « Provisioning » remplis ;
  4. `npm run capture -- --lang=fr` (puis `en`) produit les 14 screenshots, en 1440×900, `deviceScaleFactor: 2` ;
  5. `./mvnw verify`, `npm run lint && npm run test:all` et `scripts/check-docs.sh` restent verts ; aucun code de démo
     dans le jar de production.
- **Contraintes :**
  - stack figée : pas de nouvelle dépendance d'exécution ; les faux services reprennent le `HttpServer` du JDK, comme
    `OnboardingOrchestrationExampleIT` ;
  - code de démo seulement sous `src/embedded` (build `embedded-db`), comme `MailRoomDemoSeeder` ;
  - le seeding passe par les vrais cas d'usage (ACL, validation, audit), jamais par SQL direct ;
  - la garde SSRF n'est pas assouplie : `http` vers la boucle locale est déjà permis en profil `local`
    (`OutboundUrlGuard`, A13) ;
  - les identifiants `demo` / `demo` ne sont utilisés que contre le Keycloak local du realm de développement ;
  - pas d'exposition MCP nouvelle.
- **Validé par l'utilisateur le :** 2026-11-11

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Construction du profil de démo | paquets / étendre `MailRoomDemoSeeder` / script externe par l'API | paquets | tranchée par l'utilisateur (2026-11-11) |
| Données en anglais | une langue par démarrage / deux tenants / reporter | une langue par démarrage | tranchée par l'utilisateur (2026-11-11) |
| Emplacement des captures | config Playwright à part / `frontend/e2e` / projet racine | config à part | tranchée par l'utilisateur (2026-11-11) |
| Périmètre de #55 | socle + pilote / socle + 15 screenshots / socle seul | socle + pilote | tranchée par l'utilisateur : **socle + 15 screenshots** |
| Paquet autorisation riverain | garder / retirer | — | tranchée par l'utilisateur : **retiré** (2026-11-11) |
| Onboarding « Provisionné » pour S9 | faux services dans #55 / reporter à #57 / WireMock en conteneur | faux services dans #55 | tranchée par l'utilisateur (2026-11-11) |
| Sortie des captures | versionnée dans le dépôt / ignorée par git | ignorée (`frontend/capture/out/`) : le site les reprend | hypothèse |
| SLA dépassés et dossiers « anciens » | dates semées dans le passé / horloge de démo | à vérifier en conception (champs de date pilotés par l'horloge du domaine) | ouverte, sans effet sur le cadrage |

## Conception

- Faits vérifiés dans le code ou les sources :
  - `MailRoomDemoSeeder` (`src/embedded/.../demo/`) : `@Profile("demo-mailroom")`, `ApplicationReadyEvent`, base
    vide seulement, installe par les cas d'usage ;
  - `HttpRestConnector.validateBaseUrl` → `OutboundUrlGuard.assertSafe` : `http` loopback permis en profil
    `local`/`test` ;
  - `OnboardingOrchestrationExampleIT` simule les trois applications avec `com.sun.net.httpserver.HttpServer` ;
  - les tests e2e Playwright simulent le backend (`page.route`) : ils ne servent pas aux captures.
  - `SlaClock.elapsed` mesure depuis `Dossier.createdAt`, posé à `Instant.now()` par `CreateDossierUseCaseImpl` ;
    `DossierJpaEntity.created_at` et `closed_at` sont modifiables (pas de `updatable = false`) : antidater passe par
    `DossierRepository.save` (D-DEMO-BACKDATE) ;
  - l'exemple onboarding se branche par les `_placeholder` de `config/connecteurs.json`, remplacés dans le BPMN par
    l'identifiant des connecteurs créés (comme `OnboardingOrchestrationExampleIT.configureTheExample`) ;
  - un statut HTTP en erreur n'est pas un incident (tâche « Traiter l'incident ») ; une coupure réseau en est un
    (job asynchrone en *dead letter*, rejouable) : le faux service peut produire l'incident du paquet « états » ;
  - sans serveur d'envoi, l'accusé de réception de la mail room échoue dans l'outbox, pas en incident.
- Composants touchés (tous sous `src/embedded`, sauf documentation et frontend) :
  - `DemoPack` (code, ordre, `installed()`, `install()`) et `DemoSeeder`, orchestrateur
    `@Profile({"demo","demo-mailroom"})` sur `ApplicationReadyEvent` ;
  - `MailRoomDemoPack` (ex-`MailRoomDemoSeeder`), `@Profile({"demo","demo-mailroom"})` ; les autres paquets
    `@Profile("demo")` ;
  - `DemoFakeApis` (faux SIRH, IAM, ITSM, port aléatoire sur la boucle locale) ;
  - `scripts/demo.sh build | start | reset` (`demo-mailroom.sh` garde son rôle) ;
  - lot 3 : `frontend/capture/`, `frontend/playwright.capture.config.ts`, script `capture` du `package.json`.
- Scénarios de test :
  - `DemoSeederIT` (embedded-test) : sur base vide, chaque paquet est installé une fois ; relancé, rien n'est
    réinstallé ; avec `demo-mailroom` seul, seul le paquet mail room l'est (`MailRoomDemoSeederIT` adapté) ;
  - onboarding : un dossier atteint `PROVISIONNE` par le vrai processus, champs `matriculeSirh`, `identifiantIam`,
    `ticketMateriel` remplis ; un autre attend la validation du manager ;
  - états : SLA dépassé visible par le cas d'usage du rapport ; un incident rejouable ; un brouillon MCP ;
  - EN : avec `APP_DEMO_LOCALE=en`, les libellés de type et de statut sont en anglais ;
  - `scripts/check-no-embedded-db-in-jar.sh` reste vert ; captures vérifiées à la main (hors CI, D-DEMO-CAPTURE).

## Lots

Chaque lot se livre seul, en une PR (registre §31).

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1a | Socle `DemoPack` / `DemoSeeder`, paquet mail room, profil `demo`, `scripts/demo.sh`, guide | livré | #60 |
| 1b | Paquet onboarding et faux SIRH, IAM, ITSM (`DemoFakeApis`) | en revue | #61 |
| 1d | Paquet « états » : dossiers antidatés, SLA dépassés, incident rejouable, brouillon MCP | en revue | #62 |
| 2 | Données en anglais : `APP_DEMO_LOCALE`, variantes `en` des fichiers d'exemple | en cours | |
| 3 | Cadre de capture Playwright, 14 screenshots FR/EN, guide de capture | en cours | |

Prochaine action : fusionner #62, #63 et la PR du lot 3 (pour une PR empilée, la recibler sur `main` **avant** de
supprimer la branche dont elle dépend : supprimer sa base la ferme), puis revue indépendante de clôture (étape 4 du
cycle) et documentation de clôture.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-11 | `./mvnw test -Pembedded-db -Dtest='DemoSeederTest'` avant le code | rouge : `DemoPack` introuvable |
| 2026-11-11 | `./mvnw test -Pembedded-db -Dtest='DemoSeederTest,DemoSeederIT,MailRoomDemoSeederIT'` | 6 tests, 0 échec |
| 2026-11-11 | `scripts/check-docs.sh` | vert |
| 2026-11-11 | `./mvnw verify` | BUILD SUCCESS, 4 894 tests, 0 échec, 5 ignorés |
| 2026-11-11 | `./mvnw clean package -DskipTests` puis `scripts/check-no-embedded-db-in-jar.sh` | OK : jar de production sans code de démo |
| 2026-11-11 | `./mvnw clean package -Pembedded-db -DskipTests` puis `scripts/check-no-embedded-db-in-jar.sh` | refusé comme attendu (`infrastructure/demo/DemoSeeder.class`…) |
| 2026-11-11 | lot 1b : `./mvnw test -Pembedded-db -Dtest='OnboardingDemoPackIT'` avant le code | rouge : `DemoFakeApis` introuvable |
| 2026-11-11 | lot 1b : `./mvnw test -Pembedded-db -Dtest='DemoSeederTest,DemoSeederIT,MailRoomDemoSeederIT,DemoFakeApisTest,OnboardingDemoPackIT'` | 11 tests, 0 échec |
| 2026-11-11 | lot 1b : `./mvnw clean verify` puis `scripts/check-no-embedded-db-in-jar.sh` | BUILD SUCCESS, 4 894 tests, 0 échec ; jar OK |
| 2026-11-11 | lot 1b : `scripts/check-docs.sh` | vert |
| 2026-11-11 | lot 1d : `./mvnw test -Pembedded-db -Dtest='StatesDemoPackIT'` avant le code | rouge : objectif SLA de la facture absent |
| 2026-11-11 | lot 1d : les 6 classes de tests de démo (`DemoSeederTest` … `StatesDemoPackIT`) | 13 tests, 0 échec |
| 2026-11-11 | lot 1d : `./mvnw clean verify` puis `scripts/check-no-embedded-db-in-jar.sh` | BUILD SUCCESS, 4 894 tests, 0 échec ; jar OK |
| 2026-11-11 | lot 1d : `scripts/check-docs.sh` | vert |
| 2026-11-11 | lot 2 : `DemoTranslationsTest` et `DemoExampleTest` avant les traductions et le code | rouge : traductions et `DemoExample` absents |
| 2026-11-11 | lot 2 : `DemoTranslationsTest` | 4 tests, 0 échec |
| 2026-11-11 | lot 2 : les 8 classes de tests de démo, dont `DemoLocaleIT` (démo complète en anglais) | 19 tests, 0 échec |
| 2026-11-11 | lot 2 : `./mvnw clean verify` puis `scripts/check-no-embedded-db-in-jar.sh` | BUILD SUCCESS, 4 898 tests, 0 échec ; jar OK |
| 2026-11-11 | lot 2 : `scripts/check-docs.sh` | vert |
| 2026-11-11 | lot 3 : démo réelle (`scripts/demo.sh start`, exécuteur asynchrone actif) en FR puis en EN, bases neuves | trois paquets installés ; révélé et corrigé : blocage du pilote de jobs, compte `demo` hors des groupes, éditeur DMN incapable d'ouvrir du DMN 1.1 |
| 2026-11-11 | lot 3 : `CAPTURE_LANG=fr npm run capture` puis `CAPTURE_LANG=en npm run capture` | 14 captures par langue, 0 échec ; images relues |
| 2026-11-11 | lot 3 : `DemoGroupMembershipTest`, `StatesDemoPackIT`, `OnboardingDemoPackIT` | verts |
| 2026-11-11 | lot 3 : `npm run lint`, `vue-tsc -b`, `tsc` des scripts de capture | 0 erreur |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
