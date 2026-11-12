# §136 — Capsule vidéo L2 « onboarding » : pannes commandées, vignette du processus

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-12, issue [#57](https://github.com/superjojo79/Myrmacta/issues/57), vague `vague-vitrine`). Cadrage
validé par l'utilisateur le 2026-11-12. Livré en deux lots (PR #91 : pannes commandées et scène ; PR #92 : outillage
multi-capsule, tournage, vidéos, clôture). `scripts/video.sh capsule l2-onboarding` produit la capsule L2 en français et
en anglais (2 min 44, huit chapitres en deux parties, sous-titres) : plus courte que les 3 à 4 min visées, ce que
l'utilisateur a accepté pour la capsule L1 tant qu'elle est complète. Prolonge
[§127](127-demo-par-paquets-et-captures-du-site.md) (paquets de démo, faux services) et
[§128](128-capsules-video-du-site.md) (outillage des capsules). Build `embedded-db` et outillage de développement
seulement, jamais en production. Comportement : [spec 47](../specs/47-postgresql-embarque-tests-demo.md) ; procédure :
[GETTING-STARTED](../guides/GETTING-STARTED.md) § « Capsule vidéo L2 ». Revue de clôture : aucun constat critique, haut
ou moyen ; les constats bas sont corrigés (requêtes de navigateur refusées, en-tête `Allow`, dossier de sortie par
défaut des scripts Python, aide de `video.sh`, texte nuancé sur les connecteurs).

**Constat.** La capsule L2 filme l'exemple `exemples/onboarding-collaborateur` en deux parties. La partie 1 suit le
parcours nominal (scénario de recette O1). La partie 2 montre la robustesse (O2) : l'IAM en erreur puis
« Réessayer », l'ITSM en erreur puis « Abandonner », avec la compensation. Les faux SIRH, IAM et ITSM de la démo
répondaient toujours en succès, hormis une coupure du SIRH déclenchée par un domaine d'adresse réservé. Le paquet
onboarding semait trois arrivées, qui encombreraient le tournage.

**D-DEMO-FAKE-FAULTS** — une panne se commande sur une étape des faux services.

- Étapes : `sirh` (création de la fiche), `iam` (création du compte), `groups` (ajout au groupe) et `itsm` (ticket).
- Paramètres : un statut HTTP de 400 à 599, pour 1 à 10 appels. Ensuite, l'étape répond de nouveau en succès.
- Route de pilotage, sur la même écoute de la boucle locale : `POST /_demo/faults` arme une panne
  (`{"step":"iam","status":503,"times":1}`), `DELETE /_demo/faults` les désarme toutes. Une commande hors bornes
  reçoit 400.
- Les connecteurs de l'exemple n'atteignent pas cette route : leurs chemins restent sous `/sirh`, `/iam` ou `/itsm`, et
  les segments `..` sont refusés. Un administrateur de la scène (profil `local`, boucle locale autorisée) pourrait créer
  un connecteur qui la vise : c'est un outil de démo sur le poste, jamais dans le jar de production.
- Une page web ouverte sur le poste ne la commande pas : une requête qui porte un en-tête `Origin` reçoit 403, et un
  `POST` doit déclarer `application/json` (415 sinon), ce qu'un navigateur ne fait pas sans pré-vérification CORS.
- Les compensations (`DELETE`) ne sont jamais en panne.
- La route n'est pas enregistrée dans le journal des requêtes.

Écartés :

- un marqueur dans les données (domaine d'adresse) : l'adresse étrange se voit à l'écran, et « échouer une fois puis
  réussir » demanderait un état par adresse ;
- WireMock : une dépendance de plus pour quatre routes.

**D-VIDEO-OFFSCREEN-PREP** — scène et préparation de la partie 2.

- Le profil `demo-video-onboarding` installe l'exemple onboarding sans arrivée. Le paquet est tenu pour installé
  quand le processus est déployé.
- La partie 1 est filmée de bout en bout dans l'interface : l'encodage RH, le renvoi pour correction, l'approbation
  et la validation IT.
- Les arrivées de la partie 2 sont encodées et approuvées par l'API hors champ. Ces attentes sont coupées au montage.
- La caméra reprend à l'incident.

Écarté : tout filmer, car l'encodage et l'approbation seraient répétés trois fois et la capsule dépasserait 4 min.

**D-VIDEO-PIP** — pendant les appels SIRH, IAM et ITSM, le diagramme de l'instance (`BpmnViewer` de la fiche, nœuds
actifs et terminés) est posé en vignette dans un coin de l'écran filmé. Il s'agit d'un style appliqué par le metteur
en scène au tournage, à côté des champs « Provisioning » qui se remplissent : une seule prise, aucune modification du
produit. Écartés : le défilement entre champs et diagramme, moins parlant ; deux prises superposées par ffmpeg, avec
un risque de décalage.

**Outillage :** chaque capsule a son dossier de sortie, `frontend/capture/out/film/<capsule>/<langue>/`.
Deux capsules tournées dans la même langue ne mélangent ainsi ni leurs voix ni leurs vidéos. `voice.py` et `assemble.py` y écrivent par défaut
(`frontend/capture/out/film/<id de la capsule>`), comme le tournage y lit.

**Impacte :** `DemoFakeApis`, `OnboardingDemoPack`, `DemoSeeder`, `scripts/video.sh`, `frontend/capture/film/`,
spec 47, GETTING-STARTED.
