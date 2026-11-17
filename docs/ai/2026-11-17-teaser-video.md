# Teaser « Myrmacta en 90 secondes » — document de travail

Issue : #59 · Spec du domaine : docs/specs/47-postgresql-embarque-tests-demo.md · Points du registre : §128, §140,
[§148](../decisions/148-teaser-video-du-site.md)

## Cadrage

- Problème et utilisateurs : la page d'accueil de myrmacta.be attend une vidéo d'accroche courte. Elle est vue en
  lecture automatique par des intégrateurs et leurs clients qui découvrent le produit. Elle doit pouvoir se régénérer
  quand l'interface change, comme les autres capsules (D-VIDEO-PIPELINE).
- Périmètre :
  - un teaser **muet**, sans voix ni musique : seul le texte porte le message (sous-titres incrustés, comme
    D-VIDEO-SHORT) ;
  - deux versions, FR et EN ;
  - trois thèmes, chacun ouvert par un carton de punchline « Myrmacta » + un mot : **flow** (BPMN), **decide** (DMN),
    **sign** (signature électronique). Les mots restent en anglais dans les deux versions ;
  - un carton d'ouverture et une carte de fin ;
  - les cartons (ouverture, trois punchlines, fin) sont rendus par **Remotion**. Le montage reste dans le pipeline
    existant (ffmpeg) ;
  - les images de flow et decide sont des **extraits des capsules déjà tournées** (L1, L2, C1 à C12), choisis par une
    liste de coupes versionnée ;
  - les images de sign viennent d'une **nouvelle capsule courte C13**, tournée sur le bac à sable **Yousign**, avec un
    **nouvel exemple dédié** qui porte l'étape de signature.
- Hors périmètre :
  - voix off, musique, versions « avec voix » demandées par le texte de l'issue (à mettre à jour) ;
  - cartons Remotion pour les 14 capsules existantes : elles gardent `card.html` ;
  - thèmes au-delà de flow, decide et sign ;
  - DocuSign, le parcours eID.
- Critères de succès (vérifiables) :
  1. une commande de `scripts/video.sh` produit le teaser FR et EN : MP4 1920×1080, 30 i/s, H.264, sans piste audio,
     90 s au plus, sous-titres incrustés, `.vtt` et `.srt` à côté ;
  2. les cinq cartons sont rendus par Remotion à partir des textes du fichier du teaser, par la même commande, dans
     la langue demandée ;
  3. les extraits viennent des tournages régénérables : une coupe qui sort de la durée de son plan, ou trop courte
     pour la lecture de ses sous-titres, fait échouer le montage avec un message clair ;
  4. le thème sign montre une vraie signature dans le bac à sable Yousign, et le dossier qui passe à « signé » dans
     Myrmacta ;
  5. sans clés Yousign dans `.env`, la scène de C13 s'arrête avec un message qui dit quoi renseigner ;
  6. la logique nouvelle du montage et du rejeu du webhook a ses tests Python ;
  7. chaque thème a son texte, écrit dans un esprit marketing, mais **chaque affirmation à l'écran est un fait
     vérifié** (table « Textes et preuves » ci-dessous). Une affirmation sans preuve est retirée, pas adoucie.
- Contraintes :
  - stack : Remotion est un outil de développement, dans un projet Node à part (React), hors `frontend/`, hors jar,
    hors `test:all`, hors CI (vérifié : la CI et Dependabot ne lisent que `frontend/package-lock.json` et Maven).
    Versions `remotion` et `@remotion/*` identiques et exactes ;
  - licence Remotion : gratuite (particulier ou société de 3 employés au plus, confirmé le 2026-11-17) ;
  - tenant : sans objet (démo embarquée) ; MCP : sans objet ;
  - sécurité : clés du bac à sable Yousign dans `.env` (ignoré par git), jamais versionnées ni écrites dans une
    conversation. Dans le produit, ce sont des champs du connecteur `signature` (environnement, clé d'API, secret du
    webhook `YOUSIGN_HMAC`) : la scène les lit dans `.env` pour créer le connecteur. Signataire fictif sur un domaine
    réservé (`example.com`) ; Myrmacta crée la demande avec `delivery_mode: none` et `no_otp`
    (`YousignHttpClient`) : Yousign n'envoie rien au signataire ;
  - documentation : spec 47, `docs/guides/GETTING-STARTED.md`, `.env.local.example`, §148, texte de l'issue #59.
- Validé par l'utilisateur le : 2026-11-17, avec l'ajout du critère 7 (textes marketing, faits vérifiés seulement).

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| D'où viennent les images ? | extraits, tournage dédié, mélange | extraits | tranchée → §148 D-VIDEO-TEASER |
| Quels cartons fait Remotion ? | teaser seul, toutes les capsules | teaser seul | tranchée → §148 D-VIDEO-REMOTION |
| Voix et musique ? | piste CC0, piste fournie, aucune | — | tranchée : ni voix ni musique → §148 D-VIDEO-TEASER |
| Place de la punchline | carton, incrustée, les deux | carton | tranchée → §148 D-VIDEO-TEASER |
| Thèmes | flow, decide, sign ; davantage | les trois | tranchée → §148 D-VIDEO-TEASER |
| Fournisseur du thème sign | Yousign, DocuSign, simulation | Yousign | tranchée → §148 D-VIDEO-SIGN |
| Langue des mots de la punchline | anglais partout, traduits | anglais partout | tranchée → §148 D-VIDEO-TEASER |
| Licence Remotion | gratuite, à vérifier | — | tranchée : gratuite → §148 D-VIDEO-REMOTION |
| Source exacte des extraits | `clips/NN-<plan>.mp4`, vidéos brutes `.webm` | `clips/` | tranchée : vérifié, l'incrustation n'a lieu qu'à la concaténation finale (`assemble.py`) |
| Confirmation de la signature au tournage | tunnel public, webhook rejoué en local, plan coupé | rejeu local | tranchée → §148 D-VIDEO-SIGN-WEBHOOK |
| Scénario de la signature | contrat d'arrivée (onboarding), facture (mail room), nouvel exemple | contrat d'arrivée | tranchée par l'utilisateur : nouvel exemple dédié → §148 D-VIDEO-SIGN |
| Contenu du nouvel exemple | — | une convention fournisseur produite en PDF depuis un modèle, envoyée en signature au fournisseur (signataire externe, lien du portail puis Yousign) ; le dossier passe « signé » et le processus reprend | hypothèse, à préciser au lot 3 |
| Minutage | — | ouverture 4 s ; flow 1,5 + 5 × 5 s ; decide 1,5 + 3 × 5 s ; sign 1,5 + 3 × 5 s ; fin 6 s ≈ 70 s | hypothèse, ajustée au montage (90 s au plus) |

## Conception

### Faits vérifiés dans le code ou les sources

- Montage : `scripts/video/assemble.py`. 1920×1080, 30 i/s ; plans intermédiaires dans `<out>/<lang>/clips/` ;
  sous-titres incrustés seulement à la concaténation (`burn_filter`). Fonctions réutilisables : `subtitles`,
  `burn_filter`, `concat_line`, `probe_duration`, `tool`. Minutage à la lecture : `voice.py` (`READ_CPS = 15`,
  `MIN_READ = 2.2`).
- Plans disponibles (FR et EN) dans ce worktree, durées FR : C2 `01-parcours` 15,4 s, `02-bpmn` 14,2 s, `03-dmn`
  22,4 s ; C11 `01-groupes` 11,7 s ; C8 `02-rejeu` 14,6 s ; C10 `01-brouillon` 13,2 s, `02-publication` 10,5 s ;
  L1 `01-facture` 31,4 s ; L2 `03-provisioning` 20,7 s. Les plans de L1 et L2 ont une piste voix : le montage la
  retire (`-an`).
- Cartons actuels : `frontend/capture/panel/card.html` (fond `#0d1b2a` → `#1b263b`, accent `#4cc9f0`, texte
  `#f1f5f9`, Segoe UI), logo `frontend/src/assets/brand/myrmacta-logo-dark.svg`.
- Poste : Node 24.13, npm 11.6, ffmpeg 9.0.2, Edge installé (`C:\Program Files (x86)\Microsoft\Edge\Application`).
- Signature : port et cycle de vie, spec 30 ; Yousign, §109 et `YousignHttpClient` (`delivery_mode: none`,
  `no_otp`, `electronic_signature`) ; événement `signature_request.done` → `SIGNED` (`YousignCallbackParser`) ;
  aucune relève périodique du statut : seul le webhook (`CallbackWebhookController`, connecteur `signature`)
  confirme. Les adaptateurs n'ont jamais été essayés contre le bac à sable (§109 : « vérifié dans les docs
  publiques »).
- Aucun exemple (`exemples/`) n'a d'étape de signature. Précédent de variante de scène : `RiverainDemoPackIT`
  (`app.demo.riverain.variant`).
- Positionnement : spec 31, D-POSITIONING = C ; la spec demande de nuancer « sans développement ».

### Composants touchés

- `scripts/video/remotion/` (nouveau) : projet Remotion, compositions `Opening`, `Punchline` (mot, accroche),
  `Closing` ; `package.json` et `package-lock.json` aux versions exactes ; rendu par `npx remotion render` avec
  `--browser-executable` vers Edge, `--codec=h264 --pixel-format=yuv420p --muted`.
- `scripts/video/capsules/t00-teaser.json` (nouveau, `"format": "teaser"`) : la suite des éléments
  `{ "card": "opening" | "punchline" | "closing", … }` et `{ "cut": { "capsule", "plan", "start", "end" },
  "captions": { "fr": [], "en": [] } }`, avec les textes des cartons dans les deux langues.
- `scripts/video/teaser.py` (nouveau) : rend les cartons (appelle Remotion), découpe les extraits dans les `clips/`
  des capsules, contrôle les coupes et la durée totale, minute les sous-titres, concatène et incruste. Il importe les
  fonctions d'`assemble.py` au lieu de les recopier.
- `scripts/video.sh` : sous-commande `teaser` (langue par `APP_DEMO_LOCALE`, comme `capsule`) ; scène `c13-…`.
- Lot 3 : nouvel exemple `exemples/<convention>/` (FR et `en/`), paquet de démo et profil de scène
  `demo-video-signature`, connecteur `signature` Yousign créé depuis `.env`, refus de démarrer sans les clés.
- Lot 4 : `frontend/capture/film/c13-signature.film.ts`, `scripts/video/capsules/c13-signature.json`,
  `scripts/video/sign_webhook.py` (rejeu du webhook, D-VIDEO-SIGN-WEBHOOK), thème sign du teaser.

### Scénarios de test

- Python (`uv run --with pytest … pytest scripts/video -q`) :
  - une coupe hors de la durée de son plan, ou plus courte que la lecture de ses sous-titres, est refusée avec la
    capsule, le plan et la langue dans le message ;
  - un plan absent dans une langue donne la commande pour le tourner ;
  - les repères de sous-titres sont décalés du début de leur coupe dans le teaser ;
  - un teaser de plus de 90 s est refusé ;
  - les props de chaque carton sont prises dans la bonne langue, et le mot de punchline reste le même en FR et en EN ;
  - le rejeu du webhook : corps `signature_request.done`, en-tête `X-Yousign-Signature-256` = HMAC-SHA256 du corps
    brut, vérifié contre le même vecteur que les tests Java du connecteur.
- Remotion : `npx tsc --noEmit` ; images fixes des cartons regardées avant le rendu complet.
- Java (lot 3) : l'exemple s'installe en FR et en EN (comme `DemoExampleTest`) ; la scène avec le fournisseur
  `stub` amène un dossier jusqu'à l'attente de signature, puis jusqu'à « signé » ; la scène refuse de démarrer sans
  les clés Yousign.
- Pas de nouvel endpoint ni d'outil MCP : pas de test de chaîne HTTP nouveau.

### Textes et preuves

Brouillon, à relire par l'utilisateur. Chaque phrase renvoie à sa preuve ; les preuves « tournage C13 » restent à
établir au lot 4, et la phrase est retirée si le plan ne la montre pas.

| Où | FR | EN | Preuve |
|---|---|---|---|
| Ouverture | Vos flux métier, du courrier à la signature. | Your business flows, from incoming mail to signature. | L1 `01-facture` (courrier entrant), C13 (signature) ; spec 49, spec 30 |
| flow, accroche | Décrivez le parcours, Myrmacta l'orchestre. | Describe the journey, Myrmacta runs it. | C2 `01-parcours` ; spec 44 (parcours compilé en BPMN), spec 05 |
| flow, C2 `parcours` repère 2 | Le parcours devient un processus BPMN, prêt à relire. | The journey becomes a BPMN process, ready for review. | C2 (filmé : « compilé en processus BPMN, à relire avant publication »), spec 44 |
| flow, C11 `groupes` repère 1 | Chaque tâche va à l'équipe qui doit la traiter. | Each task goes to the team that handles it. | C11 (filmé : « le processus confie chaque tâche à un groupe ») |
| flow, L2 `provisioning` repère 0 | Le processus pilote vos applications : SIRH, puis IAM. | The process drives your applications: HR system, then IAM. | L2 (filmé : fiche SIRH puis compte IAM), spec 06 |
| flow, C8 `rejeu` repère 1 | Une panne ? Un clic rejoue l'étape, le dossier reprend. | An outage? One click replays the step; the case resumes. | C8 (filmé : panne au plan 1, rejeu au plan 2) |
| decide, accroche | Les règles changent, pas le code. | Rules change, code doesn't. | spec 25, spec 28 ; C2 `dmn` et C10 (une nouvelle version de table, aucun code) |
| decide, C2 `dmn` repères 1–2 | Une nouvelle version de la règle se teste sur un cas. / Erreur ? Un clic restaure la précédente. | A new version of the rule is tested on a case. / Wrong? One click restores the previous one. | C2 (filmé), spec 28 |
| decide, C10 `brouillon` repère 0 et `publication` repère 0 | Un agent IA propose une règle par MCP… / … une personne la relit et la publie. | An AI agent proposes a rule over MCP… / … a person reviews and publishes it. | C10 (filmé), spec 46 |

Lot 2 : les extraits de L1 sont retirés (L1 n'est tournée qu'en français ici, §148). L'ouverture garde « du courrier »
sur la preuve spec 49 et L1 FR.
| sign, accroche | Signé, et le dossier avance. | Signed, and the case moves on. | spec 30 (le processus reprend après le webhook) ; tournage C13 |
| sign | Le document part en signature électronique chez Yousign. | The document goes out for e-signature with Yousign. | §109 ; tournage C13 |
| sign | Le signataire signe en ligne, sans compte Myrmacta. | The signer signs online, with no Myrmacta account. | spec 30 §2.4 (portail tokenisé) ; tournage C13 |
| sign | Le PDF signé et sa preuve rejoignent le dossier, puis le processus reprend. | The signed PDF and its proof join the case, then the process resumes. | spec 30 (Webhook) ; tournage C13 |
| Fin | Des flux métier configurés, pas développés. | Business flows, configured, not coded. | C9, L2 conclusion ; spec 31 (`scriptTask` écarté, §57) |
| Fin | Conçus par l'intégrateur, ajustés par le métier. | Designed by integrators, fine-tuned by the business. | spec 31, D-POSITIONING = C |
| Fin | Avec une IA qui peut tourner sur vos postes. | With AI that can run on your own machines. | L1 `03-agent` (modèle local, D-VIDEO-AGENT), C6, spec 48 |

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | Projet Remotion des cinq cartons, FR et EN, rendus par `scripts/video.sh cards` ; images vérifiées | livré | #126 |
| 2 | `t00-teaser.json`, `teaser.py`, `scripts/video.sh teaser` : teaser avec ouverture, flow, decide et fin ; tests Python | livré | #126 (même branche) |
| 3 | Nouvel exemple avec étape de signature, scène `demo-video-signature`, connecteur Yousign depuis `.env` ; tests Java | livré | #126 (même branche) |
| 4 | Capsule C13 sur le bac à sable Yousign, rejeu du webhook, thème sign du teaser ; documentation, revue, clôture | à faire | |

Branche : `feat/59-teaser-video`, dans le worktree `.claude/worktrees/issue-55-outillage-demo` (sorties des capsules
sous la main, non versionnées). Le remote `origin` de ce checkout pointe encore vers l'ancien dépôt FlowForge : la PR
part vers `superjojo79/Myrmacta`.

Lot 1, constats pour la suite :

- les cartons Remotion sortent en `yuvj420p` (pleine échelle), les plans des capsules en `yuv420p` : le montage du
  lot 2 ramène tout en `yuv420p` (échelle limitée) avant la concaténation ;
- navigateur de rendu : Chromium headless de Playwright (Edge : trois minutes par image, écarté) ;
- durées des cartons (s) : ouverture 6, flow 5, decide 4,5, sign 4,5, fin 11,5, soit 31,5 s ; reste environ 58 s pour
  les extraits.

Lot 2, constats pour la suite :

- teaser actuel : 67,5 s (FR), 67,6 s (EN), 7 coupes, 8 sous-titres ; reste environ 22 s pour le carton sign (4,5 s)
  et les coupes de C13 ;
- les plans des capsules longues s'ouvrent sur un carton de chapitre (2,6 s) : `"from": 2.7` l'écarte ;
- dans la coupe L2, la pastille « Sous le capot » de la capsule longue dépasse un peu à gauche du sous-titre :
  accepté, à revoir si l'utilisateur la juge gênante.

Lot 3, constats pour la suite :

- exemple `exemples/convention-fournisseur` (hypothèse « convention fournisseur » retenue) ; dossier d'exemple
  « Convention — Imprimerie Lambert », signataire `claire.lambert@imprimerie-lambert.example` (Mailpit reçoit le lien) ;
- le signataire Yousign porte le libellé du bloc (« Fournisseur » / « Supplier ») : Myrmacta n'envoie pas le nom du
  champ `signataireNom` (nom affiché = `name` du `flowable:signer`). Assez pour la vidéo ; une évolution produit
  (nom du signataire lu dans le dossier) serait une issue à part ;
- risque à lever au lot 4 : Yousign peut refuser une adresse en `.example` ; sinon, une variable de scène pour
  l'adresse du signataire ;
- `scripts/video.sh start c13-signature` attend le JSON de la capsule (lot 4).

Prochaine action : lot 4, après dépôt des clés du bac à sable dans `.env` (`YOUSIGN_SANDBOX_API_KEY`,
`YOUSIGN_WEBHOOK_SECRET`).

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-17 | `uv run --with pytest --with pyjwt pytest scripts/video -q` | 31 réussis (dont 10 de `test_cards.py`) |
| 2026-11-17 | `cd scripts/video/remotion && npx tsc --noEmit` | 0 erreur |
| 2026-11-17 | `scripts/video.sh cards`, FR puis EN | 10 MP4 1920×1080, 30 i/s, H.264, sans son, durées exactes ; planches d'images regardées |
| 2026-11-17 | `scripts/check-docs.sh` | tous les contrôles `ok` |
| 2026-11-17 | `./mvnw verify` (GreenMail de la scène vidéo arrêté le temps du build : il tient le port 3143 des tests IMAP) | code 0 ; 5 297 tests, 0 échec, 0 erreur, 5 ignorés |
| 2026-11-17 | lot 2 : `uv run --with pytest --with pyjwt pytest scripts/video -q` | 44 réussis (dont 13 de `test_teaser.py`) |
| 2026-11-17 | lot 2 : `scripts/video.sh teaser`, FR puis EN | MP4 1920×1080, 30 i/s, H.264, yuv420p `tv`, sans son, 69,8 s / 70,0 s puis 67,5 / 67,6 s sans le carton de chapitre L2 ; couleurs des cartons et des plans identiques avant et après montage (pixels comparés) ; planches regardées |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
