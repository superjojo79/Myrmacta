# §148 — Teaser vidéo du site : extraits des capsules, cartons Remotion, signature Yousign

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-17, issue [#59](https://github.com/superjojo79/Myrmacta/issues/59), vague `vague-vitrine`). Cadrage
validé par l'utilisateur le 2026-11-17. Livré en cinq lots dans la PR #126 : cartons Remotion ; montage flow et
decide ; exemple convention fournisseur et scène de signature ; capsule C13 sur le bac à sable Yousign et thème sign ;
revue indépendante et clôture. Prolonge
[§128](128-capsules-video-du-site.md) (D-VIDEO-PIPELINE) et [§140](140-capsules-courtes-du-site.md) (D-VIDEO-SHORT).

**Constat.** La page d'accueil de myrmacta.be attend une vidéo d'accroche, « Myrmacta en 90 secondes ». Elle est vue
en lecture automatique, donc sans son. Les capsules L1, L2 et C1 à C12 existent, en français et en anglais, et se
régénèrent (`scripts/video.sh`). Leurs cartons sont une page HTML filmée (`frontend/capture/panel/card.html`), dont le
seul mouvement est un fondu d'entrée.

**Décisions tranchées par l'utilisateur (2026-11-17).**

- **D-VIDEO-TEASER** — le teaser est muet : ni voix ni musique, seul le texte porte le message (sous-titres incrustés,
  minutés à la lecture comme D-VIDEO-SHORT). Deux versions, FR et EN. Trois thèmes, chacun ouvert par un carton
  « Myrmacta » suivi d'un mot : **flow** (processus BPMN), **decide** (tables DMN), **sign** (signature électronique).
  Ces mots restent en anglais dans les deux versions. Un carton d'ouverture et une carte de fin encadrent les thèmes.
  - Les images de flow et decide sont des **extraits des capsules déjà tournées**, pris dans leurs plans montés
    sans sous-titres (`<capsule>/<langue>/clips/NN-<plan>.mp4`). La liste des coupes est versionnée. Une coupe qui
    sort de la durée de son plan, ou qui dure moins que la lecture de ses sous-titres, fait échouer le montage.
  - Une coupe se désigne par des repères de sous-titres de son plan, pas par des secondes (lot 2) : les plans n'ont
    pas la même durée en FR et en EN, les repères si, au texte près. Chaque sous-titre du teaser remplace un repère et
    s'affiche à son moment : l'image montre ce qu'il dit. Il doit se lire dans le temps du repère.
  - L1 n'est tournée qu'en français dans le worktree des captures : le teaser n'en prend pas d'extrait. Les preuves
    prévues sur L1 passent par C2, C11, L2, C8 et C10.
  - Écartés : un tournage dédié à tout le teaser (un film de plus à maintenir) ; la punchline incrustée sur les
    extraits (elle demande une vidéo transparente ou un montage hors ffmpeg).
- **D-VIDEO-REMOTION** — les cinq cartons du teaser (ouverture, trois punchlines, fin) sont rendus par Remotion, dans
  un projet Node à part (`scripts/video/remotion/`, React). Les textes viennent du fichier JSON du teaser et passent
  en `--props`. Le montage reste dans le pipeline existant (ffmpeg). Outillage de développement, comme D-VIDEO-PIPELINE :
  hors `frontend/`, hors jar, hors `test:all`, hors CI et hors Dependabot. Versions `remotion` et `@remotion/*`
  identiques et exactes (4.0.540, React 19.3.1). Police système (Segoe UI) et couleurs de `card.html` ; logo lu dans
  `frontend/src/assets/brand/` (`--public-dir`), sans copie : pas d'appel réseau au rendu.
  - Navigateur de rendu : le Chromium headless de Playwright, déjà installé pour les captures
    (`chromium_headless_shell-*`), environ 4 s par carton. Edge, essayé d'abord (lot 1), ouvre une vraie fenêtre et
    met trois minutes par image. `REMOTION_BROWSER` désigne un autre exécutable.
  - Chaque carton dure au moins le temps que son texte apparaisse puis soit lu (15 caractères par seconde, au moins
    2,2 s, règle de D-VIDEO-SHORT) ; sinon `cards.py` refuse de le rendre.
  - Licence : Remotion est gratuit pour un particulier ou une société de 3 employés au plus (confirmé par
    l'utilisateur le 2026-11-17). À revoir si l'équipe dépasse ce seuil.
  - Écarté : Remotion pour les cartons des 14 capsules existantes (toutes à remonter, et Remotion deviendrait
    indispensable à tout le pipeline).
- **D-VIDEO-SIGN** — le thème sign se filme sur le **bac à sable Yousign**, en une capsule courte de plus, C13
  (`c13-signature`), dont le teaser prend des extraits comme pour les autres thèmes. La clé d'API et le secret du
  webhook du bac à sable sont dans `.env` (ignoré par git) ; la scène les lit pour créer l'instance du connecteur
  `signature`. Sans eux, la scène refuse de démarrer et dit quoi renseigner.
  - Aucun exemple n'avait d'étape de signature. Un **nouvel exemple dédié** la porte (choix de l'utilisateur,
    2026-11-17), installé seulement par la scène de C13 : les exemples et les capsules existants ne changent pas.
    Écartés : une variante « contrat d'arrivée » de l'exemple onboarding, une signature interne de facture dans la
    mail room.
  - Lot 3 : l'exemple est `exemples/convention-fournisseur` (relecture, convention générée, signature du représentant
    du fournisseur, statut « Signée » ou « Non signée »), installé par `SignatureDemoPack` (profil
    `demo-video-signature`). Fournisseur `yousign` par défaut, `stub` pour répéter ; les clés sont
    `YOUSIGN_SANDBOX_API_KEY` et `YOUSIGN_WEBHOOK_SECRET`. Le libellé d'un signataire (`flowable:signer name`) s'affiche
    sur la fiche et chez Yousign : les exemples le traduisent comme un nom d'activité, ce n'est pas un code.
  - Lot 4 (premier essai réel, 2026-11-17) : le bac à sable refuse tout signataire hors de l'organisation du compte.
    `YOUSIGN_SANDBOX_SIGNER_EMAIL` (une adresse de l'organisation) remplace celle de l'exemple ; la scène refuse de
    démarrer sans elle. Le fournisseur de la scène est `VIDEO_SIGNATURE_PROVIDER` : le `.env` d'un poste fixe souvent
    `APP_SIGNATURE_PROVIDER=none`. Le signataire s'appelle « Représentant du fournisseur » : Yousign coupe le libellé en
    prénom et nom, et un seul mot donnait « Fournisseur - ».
  - **Données personnelles à l'écran** (choix de l'utilisateur, 2026-11-17) : l'adresse réelle du signataire et le
    nom du titulaire du compte Yousign sont remplacés, à l'écran seulement, par ceux de l'exemple (le tournage réécrit
    le texte affiché des pages filmées). La signature se fait avec la vraie adresse. Écarté : un membre dédié de
    l'organisation Yousign (un compte de plus à tenir). Revue de clôture : le tournage s'arrête si l'adresse ou le
    nom à masquer manque (`YOUSIGN_SANDBOX_ACCOUNT_NAME` est obligatoire), la valeur d'un champ est réécrite dès
    qu'elle est posée, et chaque plan masqué vérifie, avant d'être rangé, que ni l'adresse ni le nom ne sont à
    l'écran. Le rejeu du webhook vise la demande de la prise par son `external_id` (l'identifiant Myrmacta), jamais
    « la dernière signée » du bac à sable.

**Décision de conception (2026-11-17).**

- **D-VIDEO-SIGN-WEBHOOK** — Myrmacta n'apprend qu'une signature est faite que par le webhook de Yousign
  (D-SIGNATURE-RETURN). Le webhook ne peut pas joindre un poste de développement. Le tournage le rejoue donc en local :
  il lit dans l'API Yousign l'état réel de la demande, construit l'événement `signature_request.done` comme Yousign
  l'envoie, le signe avec le secret HMAC du connecteur, puis le poste au point d'entrée du connecteur. La suite est le
  vrai comportement du produit : téléchargement du PDF signé et de la preuve chez Yousign, reprise du processus.
  - Écarté : un tunnel public vers le poste (la démo serait exposée sur Internet, et il faudrait un compte de plus) ;
    couper le plan après la signature (le dossier signé ne serait pas montré).
  - Le rejeu est un outil de tournage, pas une fonction du produit : rien n'est ajouté au jar.
  - Lot 4 : `scripts/video/sign_webhook.py`, appelé hors champ par le plan « retour » de C13. Le secret du webhook est
    une valeur aléatoire du `.env` : aucun webhook n'est déclaré chez Yousign.

**Textes du teaser.** Ton marketing, mais chaque affirmation à l'écran est un fait vérifié : la
[spec 47](../specs/47-postgresql-embarque-tests-demo.md) tient la table « affirmation → preuve ». Une affirmation sans preuve est retirée. La carte de fin suit le
positionnement retenu ([spec 31](../specs/31-positionnement-produit.md), D-POSITIONING = C : l'intégrateur conçoit
les processus, le métier ajuste formulaires, documents et règles), et non le discours « sans développement » que la
spec 31 demande de nuancer.

**Risque connu.** Les adaptateurs Yousign (§109) n'ont été vérifiés que contre la documentation publique, jamais
contre le bac à sable. Le lot de la signature est leur premier essai réel. Un écart du produit se corrige en TDD dans
ce lot, ou part en issue s'il dépasse le teaser.

- Lot 4 : deux écarts corrigés en TDD (nom du fichier déposé ; statut des signataires d'une demande signée sans
  détail, [spec 30](../specs/30-signature-electronique.md)). Hors teaser : le préfixe « Document généré — » et les
  libellés « Document signé » et « Preuve de signature » sont écrits en français quelle que soit la langue, et le
  signataire Yousign est créé en français (`locale`) ; la version anglaise de C13 les montre.

**Impacte :** `scripts/video.sh`, `scripts/video/` (montage du teaser, rejeu du webhook, tests), `scripts/video/remotion/`,
`scripts/video/capsules/`, `frontend/capture/film/c13-signature.film.ts`, la scène de démo de la signature,
[spec 47](../specs/47-postgresql-embarque-tests-demo.md), [GETTING-STARTED](../guides/GETTING-STARTED.md),
`.env.local.example`.
