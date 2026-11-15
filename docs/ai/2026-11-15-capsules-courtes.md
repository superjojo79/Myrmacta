# Capsules courtes C1 à C12 et screenshots du site — document de travail

Issue : #58 · Spec du domaine : docs/specs/47-postgresql-embarque-tests-demo.md · Points du registre : §127, §128,
§136, §140 (à créer)

## Cadrage

- Problème et utilisateurs : les pages « Fonctionnalités » de myrmacta.be attendent des capsules de 30 à 60 s (60 à
  90 s pour C2), sans son, avec des sous-titres incrustés, qui tournent en boucle, en français et en anglais. Chacune
  a pour titre le bénéfice métier et se termine par une carte « Sous le capot ». Elles accompagnent des screenshots S1 à
  S15. Tout doit pouvoir se régénérer avec les scripts de #55, #56 et #57.
- Périmètre :
  - 10 capsules : C1, C2, C3, C4, C7, C8, C9, C10, C11, C12, en FR et en EN ;
  - le « mode court » du pipeline vidéo (voix, tournage, montage) ;
  - les 14 screenshots déjà livrés par #55, à régénérer en FR et en EN ; S3 et S5 sont à aligner sur l'issue (question
    ouverte ci-dessous).
- Hors périmètre :
  - C5 (portail usager), C6 (pré-vérification IA) et S13 (portail mobile). L'exemple riverain a été retiré de la démo
    (§127). Ces trois éléments partent dans une nouvelle issue (utilisateur, 2026-11-15) ;
  - toute capsule sur la signature électronique.
- Critères de succès :
  - pour chaque capsule et chaque langue, `scripts/video.sh capsule <id>` lancé sur une démo fraîche produit
    `frontend/capture/out/film/<id>/<langue>/<id>-<langue>.mp4` : 1920×1080, sans piste audio, sous-titres incrustés,
    30 à 60 s (60 à 90 s pour C2), carton d'ouverture avec le bénéfice et le logo, carte « Sous le capot » avec le logo,
    tenue au moins 3 s ;
  - `npm run capture` produit les 14 screenshots en FR et en EN ;
  - les preuves habituelles sont vertes (`./mvnw verify`, lint et `test:all`, `check-docs`, pytest des outils
    vidéo).
- Contraintes :
  - outillage de développement : hors jar de production, hors `test:all`, hors CI ;
  - pas de nouvelle dépendance d'exécution ;
  - aucun texte ne part chez un tiers : sans voix, pas d'appel à edge-tts.
  - Documentation à mettre à jour : GETTING-STARTED (§ « Capsules courtes »), spec 47, registre §140,
    `identifiants.md`.
- Validé par l'utilisateur le : 2026-11-15.

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Worktree | issue-55, nouveau, checkout principal | issue-55 | tranchée (utilisateur, 2026-11-15) : branche `feat/58-capsules-courtes` |
| Format court | mode court du pipeline ; minutage par la voix | mode court | tranchée (utilisateur, 2026-11-15) → D-VIDEO-SHORT |
| C5, C6, S13 sans données | profil de tournage riverain ; issue à part ; abandon | profil riverain | tranchée (utilisateur, 2026-11-15) : issue à part |
| C12 « de la recette à la production » avec une seule instance | (a) on exporte la démo, on modifie le paquet hors champ (un libellé et un connecteur de plus, comme si la recette avait avancé), puis on importe : le plan montre les créations et les mises à jour, et on applique ; (b) une seconde instance Myrmacta sur un autre port (deux bases, deux connexions Keycloak) | (a), avec un sous-titre qui annonce le paquet « venu de la recette » | tranchée (utilisateur, 2026-11-15) : (a) |
| S3 et S5 diffèrent de l'issue | S3 : éditeur BPMN seul (livré) ou concepteur de parcours avec l'éditeur en vignette (issue) ; S5 : gabarit de notification (livré) ou modèle de document avec son aperçu PDF (issue), mais la démo n'a aucun modèle de document | aligner sur l'issue : le paquet « états » ajoute un modèle de document (un courrier d'accusé), S3 devient le parcours avec une vignette | tranchée (utilisateur, 2026-11-15) : aligner |

## Conception

- Faits vérifiés dans le code :
  - tous les écrans cités existent (`frontend/src/views/{pilotage,admin}/*.vue`) ;
  - `DmnEditorView` a un testeur (évaluation, jeu de cas, versions, redéploiement d'une version) ;
  - `ParcoursView` compile un parcours en BPMN et dépose un brouillon ;
  - `TemplatesAdminView` a un `TemplatePreviewPanel` et des versions ;
  - `ConfigurationPromotionView` exporte, planifie puis applique un import ;
  - ffmpeg 9 (build complet) a libass (`subtitles`) pour incruster les sous-titres ;
  - le montage actuel (`assemble.py`) pose les sous-titres en piste `mov_text` avec une piste AAC ;
  - le tournage minute chaque plan sur `voice.json` (`Director.holdForVoice`).
- Conception (à confirmer en lot 1) :
  - **D-VIDEO-SHORT**. Capsule JSON avec `"format": "short"` et `captions` à la place de `narration`.
    - `voice.py` détecte ce format et ne parle pas : il écrit le même `voice.json` (durée et repères) d'après une
      vitesse de lecture (≈ 15 caractères/s, au moins 2,2 s par sous-titre), sans fichier audio.
    - Le tournage reste inchangé.
    - `assemble.py` monte sans audio, incruste les sous-titres (libass, style lisible sur fond sombre), garde les
      `.vtt` et `.srt` à côté et ne pose pas de chapitres.
  - Mise en scène d'une capsule courte : carton d'ouverture (bénéfice et logo), deux à quatre plans, carte de fin
    « Sous le capot » (détail technique et logo, tenue au moins 3 s). Pas de bandeau pendant les plans : le détail
    technique est sur la carte de fin.
  - **Scène** : la démo complète (`demo`, qui contient la mail room, l'onboarding et les états). Elle se lance avec
    `scripts/video.sh start c1-dossier` ou `scripts/demo.sh start`. Les capsules qui modifient l'état (C2, C4, C10,
    C12) le font sur des objets qui leur sont propres. `scripts/video.sh shorts` enchaîne les 10 capsules dans un ordre
    fixe sur une démo fraîche.
- Scénarios de test :
  - pytest pour le minutage des sous-titres (vitesse de lecture, minimum, enchaînement), le filtre d'incrustation et
    l'absence de piste audio (graphe ffmpeg) ;
  - le tournage de chaque capsule vaut test de bout en bout contre la vraie démo (contrôle image par image) ;
  - si le paquet « états » change (S5), test embarqué du modèle de document.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | Mode court (voix, montage, video.sh), C1 « dossier de bout en bout », C8 « une panne n'arrête pas le dossier » ; défaut du changement de type dans « Nouveau dossier » corrigé | livré | (cette PR) |
| 2 | C2 (parcours, BPMN, DMN testé puis restauré), C3 (question au bon moment), C4 (courriers PDF) | à faire | |
| 3 | C7 (délais, SLA, export BI), C9 (connecteurs), C10 (brouillons MCP) | à faire | |
| 4 | C11 (groupes, mobile), C12 (promotion), screenshots S1 à S15 sans S13, issue C5/C6/S13, clôture | à faire | |

Prochaine action : PR du lot 1, puis lot 2 (C2, C3, C4). C5, C6 et S13 : issue #101. Défaut relevé, non corrigé : sur un processus terminé, la fiche n'affiche plus ni le diagramme ni l'historique (issue #103).

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-15 | `uv run --with pytest --with pyjwt pytest scripts/video -q` | 19 passed |
| 2026-11-15 | `cd frontend && npm run lint && npm run test:all` | lint ok ; Vitest 1132/1132 ; Playwright 201/201 |
| 2026-11-15 | `scripts/check-docs.sh` | tous les contrôles ok |
| 2026-11-15 | `./mvnw verify` | 5209 tests, 0 échec, BUILD SUCCESS (après correctif CRLF de `EidTrustAnchorsTest`) |
| 2026-11-15 | `scripts/video.sh capsule c01-dossier` et `c08-panne`, FR et EN, sur démo fraîche | 4/4 plans par capsule ; MP4 1920×1080 sans audio, 33,7 à 34,4 s ; contrôle image par image |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
