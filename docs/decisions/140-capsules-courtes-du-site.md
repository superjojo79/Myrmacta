# §140 — Capsules courtes du site : vidéos muettes, sous-titres incrustés

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] ouvert (2026-11-15, issue [#58](https://github.com/superjojo79/Myrmacta/issues/58), vague `vague-vitrine`). Cadrage
validé par l'utilisateur le 2026-11-15. Livraison en quatre lots. Lot 1 : mode court du pipeline, capsules C1 et C8.
Prolonge [§128](128-capsules-video-du-site.md) (outillage des capsules) et [§136](136-capsule-video-onboarding.md)
(une capsule par fichier JSON, un dossier de sortie par capsule). Build `embedded-db` et outillage de développement
seulement, jamais en production. Comportement : [spec 47](../specs/47-postgresql-embarque-tests-demo.md) ; procédure :
[GETTING-STARTED](../guides/GETTING-STARTED.md) § « Capsules courtes ».

**Constat.** Les pages « Fonctionnalités » de myrmacta.be attendent douze capsules de 30 à 60 s (60 à 90 s pour C2).
Elles sont sans son et ont des sous-titres incrustés, car elles tournent en boucle. Elles existent en français et en
anglais. Leur titre dit le bénéfice métier ; une carte de fin « Sous le capot » donne le détail technique.

Les capsules longues L1 et L2 ont une voix off qui minute chaque plan, des sous-titres en piste séparée et des chapitres.

**Décisions tranchées par l'utilisateur (2026-11-15).**

- **Périmètre** : dix capsules, C1 à C4 et C7 à C12. C5 (portail usager), C6 (pré-vérification IA) et le screenshot
  S13 n'ont pas de données dans la démo, puisque l'exemple riverain en est retiré (§127). Ils partent dans l'issue
  [#101](https://github.com/superjojo79/Myrmacta/issues/101).
- **D-VIDEO-SHORT** : une capsule courte est un fichier JSON de `scripts/video/capsules/` marqué `"format": "short"`.
  Ses plans portent des `captions` au lieu d'une `narration`, et elle a une carte `underTheHood`.
  - `voice.py` ne parle pas, donc rien ne part chez Microsoft. Il écrit le même `voice.json`, avec la durée et les
    repères de chaque plan. Chaque sous-titre est tenu le temps de le lire : 15 caractères par seconde, au moins
    2,2 s.
  - Le tournage n'est pas modifié : `holdForVoice` tient chaque plan au moins le temps de ses sous-titres.
  - `assemble.py` monte sans piste audio et sans chapitres. Il incruste les sous-titres avec libass (texte blanc gras
    sur un bandeau sombre, en bas) et garde les fichiers `.vtt` et `.srt` à côté.
  - Pour l'incrustation, ffmpeg est lancé dans le dossier de sortie et reçoit un nom de fichier simple : on n'échappe
    aucun chemin Windows dans le graphe de filtres.
  - Écarté : minuter les plans par la voix puis la couper au montage (réseau et service tiers pour une vidéo muette).
- **Mise en scène** : chaque capsule commence par un carton d'ouverture (le bénéfice sous le logo). Elle se termine par
  la carte « Sous le capot » (le détail technique sous le logo, tenue 4,5 s, donc plus des 3 s demandées). Pas de
  bandeau pendant les plans.
- **Scène** : la démo complète (profil `demo`), que lance `scripts/video.sh start cNN-…`.
  - Une capsule qui modifie la démo le fait sur ses propres objets. C8 corrige l'arrivée en incident, ce qui la retire
    des incidents.
  - `scripts/video.sh shorts` enchaîne toutes les capsules courtes dans l'ordre de leur numéro, sur une démo fraîche.
- **C12** : la démo n'a qu'une instance. On exporte sa configuration, puis on modifie le paquet hors champ comme si la
  recette avait avancé. On l'importe ensuite : le plan montre les créations et les mises à jour.
- **S3 et S5** sont alignés sur l'issue :
  - S3 montre le concepteur de parcours, avec l'éditeur BPMN en vignette ;
  - S5 montre un modèle de document à côté de son aperçu PDF. Le paquet « états » ajoute ce modèle.

**Défaut corrigé au tournage (lot 1).** Dans « Nouveau dossier », passer d'un type à un autre laissait dans le
formulaire les valeurs du type affiché avant (par exemple le canal par défaut du courrier). La création était alors
refusée (400, « Unknown field »). Le formulaire n'envoie plus que les champs du type choisi
(`CreateDossierView.vue`, test Vitest).

**Défaut relevé, non corrigé ici.** Quand le processus d'un dossier est terminé, la fiche n'affiche plus ni le diagramme
ni l'historique : issue [#103](https://github.com/superjojo79/Myrmacta/issues/103). Les capsules suivent donc un dossier
en cours ; C8 cadre sur les champs « Provisioning » après le rejeu.
