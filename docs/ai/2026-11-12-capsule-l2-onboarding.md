# Capsule vidéo L2 « onboarding orchestré » — document de travail

Issue : #57 · Spec du domaine : docs/specs/47-postgresql-embarque-tests-demo.md · Points du registre : §127, §128, §136

## Cadrage

- Problème et utilisateurs : capsule longue (3 à 4 min, plus courte si complète) pour la page « Cas d'usage » de
  myrmacta.be. L'arrivée d'un collaborateur y est orchestrée entre le SIRH, l'IAM et l'ITSM, sans développement
  (exemple `exemples/onboarding-collaborateur`). Public : visiteurs du site, métier et technique.
- Périmètre :
  - capsule FR et EN en deux parties, avec voix off, sous-titres, chapitres et bandeaux « Sous le capot » ;
  - même chaîne que la capsule L1 (§128), avec plein cadre 1920×1080 et le logo sur les cartons d'ouverture et de fin ;
  - partie 1, d'après le scénario de recette O1 : encodage RH, renvoi pour correction puis approbation, appels SIRH,
    IAM et ITSM avec une vignette du processus, validation IT (DEVELOPPEURS), dossier « Provisionné » ;
  - partie 2, d'après O2 : panne IAM puis « Réessayer », panne ITSM puis « Abandonner » avec compensation.
- Hors périmètre :
  - publication sur le site ;
  - screenshots S2 et S9, qui relèvent de l'outillage de capture existant ;
  - le scénario de recette O3 ;
  - une modification de l'exemple ou du produit.
- Critères de succès (vérifiables) :
  1. `scripts/video.sh capsule l2-onboarding` produit `l2-onboarding-{fr,en}.mp4`, `.vtt` et `.srt`, en plein cadre,
     avec les deux parties ;
  2. la scène est rejouable : `reset`, puis `start l2-onboarding` remet l'exemple installé, sans arrivée et sans
     panne armée ;
  3. une panne se commande sur une étape choisie (SIRH, IAM, groupe, ITSM), pour N appels, puis le service répond de
     nouveau en succès ;
  4. la capsule L1 se tourne toujours (non-régression de l'outillage commun).
- Contraintes :
  - build `embedded-db` seulement, jamais dans le jar de production ;
  - faux services sur la boucle locale ;
  - pas de nouvelle dépendance d'exécution ;
  - pas d'exposition MCP ;
  - documentation à mettre à jour : spec 47, GETTING-STARTED (« Capsule vidéo ») et `identifiants.md`.
- Choix de l'utilisateur (2026-11-12) :
  - worktree issue-55 ;
  - pannes par une route de pilotage du faux service ;
  - vignette CSS du diagramme ;
  - arrivées de la partie 2 préparées hors champ.
- Validé par l'utilisateur le : 2026-11-12

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Commande des pannes | route de pilotage ; marqueur dans les données | route de pilotage | tranchée → §136 D-DEMO-FAKE-FAULTS |
| Vignette du processus | vignette CSS ; défilement ; deux prises et ffmpeg | vignette CSS | tranchée → §136 D-VIDEO-PIP |
| Partie 2 | préparée hors champ ; tout filmé | hors champ | tranchée → §136 D-VIDEO-OFFSCREEN-PREP |
| Sorties de deux capsules dans le même dossier `out/film/<langue>` | un sous-dossier par capsule ; préfixer les fichiers | sous-dossier `out/film/<capsule>/<langue>` | hypothèse → §136 |

## Conception

- Faits vérifiés dans le code :
  - `OnboardingDemoPack` (profil `demo`) installe l'exemple et sème trois arrivées (Élise, Thomas, Sarah) ;
  - `DemoFakeApis` (profil `demo`) répond toujours en succès, sauf la coupure SIRH sur `.invalid` ; son commentaire
    renvoie les réponses en erreur à #57 ;
  - le BPMN a quatre tâches utilisateur (`validationManager`, `correction`, `validationAcces`, `traiterIncident`),
    sans groupe candidat : le compte du tournage peut toutes les prendre ;
  - un statut HTTP hors 2xx ne fait pas échouer l'activité : il mène à `traiterIncident` ;
  - `DossierDetailView` affiche le diagramme de l'instance (`BpmnViewer`, nœuds actifs et terminés) ;
  - l'outillage vidéo (`video.sh`, `voice.py`, `assemble.py`, `director.ts`) est écrit pour une seule capsule : profil
    fixe, sorties dans `out/film/<langue>`, `testMatch` sur tous les `*.film.ts`.
- Composants touchés :
  - `DemoFakeApis` : route `/_demo/faults` ;
  - `OnboardingDemoPack` : profil vidéo, sans arrivées ;
  - `DemoSeeder` ;
  - `VideoOnboardingDemoPackIT` ;
  - `scripts/video.sh` : capsule en argument ;
  - `director.ts` : sorties par capsule, `pip()` et `fault()` ;
  - `playwright.film.config.ts` : filtre par capsule ;
  - `capsules/l2-onboarding.json` et `l2-onboarding.film.ts` ;
  - les docs.
- Scénarios de test :
  - unitaires `DemoFakeApisTest` : armer, consommer N pannes, revenir au succès, rejet des paramètres hors bornes,
    réinitialisation ;
  - IT `VideoOnboardingDemoPackIT` (embedded-db) : le profil vidéo installe l'exemple sans arrivée. Une arrivée
    approuvée avec la panne IAM armée atteint `traiterIncident` ; « Réessayer » mène à `validationAcces`. Une panne
    ITSM suivie d'« Abandonner » envoie les `DELETE` IAM puis SIRH ;
  - tests Python de l'outillage (`test_video_tools.py`) pour le choix de la capsule.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | Scène : pannes pilotables des faux services, profil `demo-video-onboarding` (exemple sans arrivées), IT, spec 47 | livré | lot 1 |
| 2 | Outillage multi-capsule (`video.sh <capsule>`, sorties par capsule), texte et tournage L2, vignette, vidéos FR et EN, docs | à faire | |
| 3 | Revue indépendante, correctifs, clôture (suppression de ce document) | à faire | |

Prochaine action : lot 2, outillage multi-capsule puis tournage L2.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-12 | `./mvnw test -Pembedded-db -Dtest=DemoFakeApisTest,OnboardingDemoPackResumeTest,VideoOnboardingDemoPackIT,OnboardingDemoPackIT,DemoSeederTest` | 23 tests, 0 échec |
| 2026-11-12 | `scripts/check-docs.sh` | tous les contrôles ok |
| 2026-11-12 | `./mvnw verify` | 4987 tests, 0 échec, 5 ignorés (existants) ; BUILD SUCCESS |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
