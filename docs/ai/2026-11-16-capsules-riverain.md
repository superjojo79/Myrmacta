# Capsules C5, C6 et screenshot S13 (exemple riverain) — document de travail

Issue : #101 (suite de #58) · Spec du domaine : docs/specs/47-postgresql-embarque-tests-demo.md, docs/specs/26-portail-externe.md
· Points du registre : §127, §140, §143 (à créer)

## Cadrage

- Problème et utilisateurs : les pages « Fonctionnalités » de myrmacta.be attendent encore trois éléments, tous montrés
  sur l'exemple `exemples/autorisation-riverain`. L'exemple a été retiré de la démo (§127).
  - C5 « L'usager complète sa demande sans compte » : le portail, avec son lien à jeton.
  - C6 « Les pièces incohérentes repérées avant l'agent » : la pré-vérification IA avec un modèle local.
  - S13 : le portail usager sur mobile.
- Périmètre :
  - une scène de tournage à part (profil `demo-video-riverain`) qui installe l'exemple, active le portail et la
    pré-vérification ;
  - la variante anglaise complète de l'exemple ;
  - les capsules C5 et C6 et le screenshot S13, en FR et en EN.
- Hors périmètre : remettre le riverain dans la démo complète ; un formulaire public de dépôt (le portail ne sert qu'à
  répondre à une tâche) ; les cas « preuve absente » et « preuve scannée » de C6 (le modèle y répond « OK » à tort).
- Critères de succès :
  - `scripts/video.sh start c05-portail` monte la scène riverain ;
  - `scripts/video.sh capsule c05-portail` et `c06-preverification` produisent des MP4 muets aux sous-titres incrustés,
    de 30 à 60 s, en FR et en EN ;
  - S13 est produit en FR et en EN ;
  - les preuves habituelles sont vertes (verify, tests embarqués de la CI, lint, `test:all`, pytest, `check-docs`) ;
  - `DemoTranslationsTest` couvre la variante anglaise.
- Contraintes :
  - code de démo sous `src/embedded` seulement, jamais dans le jar de production ;
  - aucune nouvelle dépendance ;
  - le modèle IA est local (Ollama `gemma4:12b`) : aucune donnée ne part chez un tiers.
- Validé par l'utilisateur le : 2026-11-16 (avec les deux hypothèses).

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Worktree | issue-55, nouveau, checkout principal | issue-55 | tranchée (utilisateur, 2026-11-16) : branche `feat/101-capsules-riverain` |
| Scène | profil de tournage à part ; paquet dans la démo complète | profil à part | tranchée (utilisateur, 2026-11-16) |
| Anglais | variante anglaise complète ; FR seulement | variante complète | tranchée (utilisateur, 2026-11-16) |
| Défauts révélés par la variante « avec portail » | corriger si petit, sinon s'arrêter ; toujours corriger ; toujours en issue | corriger si petit | tranchée (utilisateur, 2026-11-16) |
| Les trois BPMN partagent la clé `autorisationRiverain` | une capsule déploie hors champ la variante dont elle a besoin ; trois clés distinctes (modifier `generer-bpmn.py`) | déploiement hors champ (l'exemple ne change pas) | hypothèse validée (utilisateur, 2026-11-16) |
| Pièces de C6 en anglais | textes des cas laissés en français (documents bruxellois réalistes) ; cas traduits | laissés en français | hypothèse validée (utilisateur, 2026-11-16) |

## Conception

- Faits vérifiés dans le code :
  - portail opt-in (`APP_PORTAL_ENABLED`, `APP_PORTAL_PUBLIC_BASE_URL`, spec 26) ; le lien est envoyé par e-mail
    (outbox, vers Mailpit en scène) ;
  - pré-vérification `openai-compatible` (`APP_PRECHECK_*`), `gemma4:12b` présent dans l'Ollama local ;
  - `DemoExample` lit déjà les variantes `en/` et `en/names.json` pour le BPMN ;
  - `DemoTranslationsTest` énumère les exemples traduits.
- Composants :
  - `src/embedded/.../demo/RiverainDemoPack` (profil `demo-video-riverain`) : type, questions, configuration de
    workflow, modèle de décision (activé), gabarit de demande d'informations, BPMN sans portail ;
  - `exemples/autorisation-riverain/en/` : copies traduites et `names.json` ;
  - `scripts/video.sh` : scène et variables ;
  - capsules `c05-portail` et `c06-preverification`, films, S13.
- Tests :
  - `RiverainDemoPackIT` (embarqué, ajouté à la liste de la CI) ;
  - `DemoTranslationsTest` étendu ;
  - pytest et le tournage lui-même.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | Variante anglaise de l'exemple ; scène `demo-video-riverain` (paquet, video.sh) avec son IT | livré | #115 |
| 2 | C5 (portail) et S13, FR et EN ; portail mobile sans débordement ; consignes des BPMN traduites | livré | (cette PR) |
| 3 | C6 (pré-vérification IA), FR et EN ; revue, clôture | à faire | |

Prochaine action : PR du lot 2, puis lot 3 (C6 : tournage écrit, à jouer avec Ollama ; revue ; clôture).

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-16 | Lot 1 : `./mvnw verify` ; tests embarqués de la CI ; pytest ; `check-docs` | 5223 tests, 0 échec ; 52/52 (dont `RiverainDemoPackIT` FR et EN, et la variante avec portail déployée) ; 21 passed ; ok |
| 2026-11-16 | Lot 2 : `./mvnw verify` ; tests embarqués ; `npm run lint && npm run test:all` ; pytest ; `check-docs` | 5223 tests, 0 échec ; 52/52 ; lint ok, Vitest 1159/1159, Playwright 205/205 (E2E-PORTAL-8) ; 21 passed ; ok |
| 2026-11-16 | `scripts/video.sh capsule c05-portail`, FR et EN, sur des scènes fraîches | MP4 muets de 49,8 et 49,2 s ; S13 FR et EN ; contrôle image par image |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
