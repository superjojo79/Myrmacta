# §143 — Scène riverain des capsules C5 et C6 et du screenshot S13

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-16, issue [#101](https://github.com/superjojo79/Myrmacta/issues/101), vague `vague-vitrine`).
Cadrage validé par l'utilisateur le 2026-11-16. C'est la suite de [§140](140-capsules-courtes-du-site.md) : les capsules
C5 et C6 et le screenshot S13 avaient été reportés, faute de données. Livré en trois lots : lot 1 (PR #115), variante anglaise de l'exemple et scène ; lot 2 (PR #116), C5 et S13 ; lot 3
(PR de clôture), C6.
Build `embedded-db` et outillage de développement seulement, jamais en production. Comportement :
[spec 47](../specs/47-postgresql-embarque-tests-demo.md).

**Constat.** C5 (« L'usager complète sa demande sans compte » : le portail et son lien à jeton), C6 (« Les pièces
incohérentes repérées avant l'agent » : la pré-vérification IA) et S13 (le portail sur mobile) se montrent sur
l'exemple `exemples/autorisation-riverain`. Or cet exemple a été retiré de la démo complète (§127) et n'avait pas de
variante anglaise. Sa variante « avec portail » n'avait jamais été déployée par le serveur, faute de portail activé.

**Décisions tranchées par l'utilisateur (2026-11-16).**

- **D-VIDEO-RIVERAIN-SCENE** : une scène de tournage à part, le profil `demo-video-riverain`
  (`scripts/video.sh start c05-…`).
  - Le paquet `RiverainDemoPack` installe l'exemple par les vrais cas d'usage, dans l'ordre de mise en service de son
    README :
    - le type, avec ses types de pièces ;
    - les questionnaires et la configuration du workflow ;
    - le modèle de décision et le gabarit de demande d'informations, activés ;
    - en dernier, la variante du processus que demande la capsule (`app.demo.riverain.variant`) : avec portail pour C5
      et S13, avec pré-vérification pour C6 (voir la revue de clôture).
  - La scène active le portail (liens envoyés à Mailpit). Elle active aussi la pré-vérification IA, avec le modèle
    local d'Ollama (`gemma4:12b` par défaut) : aucune donnée ne part chez un tiers.
  - Le riverain ne revient pas dans la démo complète, et `scripts/video.sh shorts` saute C5 et C6.
- **Variante anglaise complète** de l'exemple (D-DEMO-LOCALE-FILES), vérifiée par `DemoTranslationsTest` :
  - `en/config/` : type, questionnaires, configuration, modèle et gabarit ;
  - `en/names.json` pour les trois BPMN ;
  - `en/template-decision.html`.
- **Défauts révélés par la variante avec portail** : un correctif court est fait ici, en TDD ; pour un défaut plus
  lourd, on s'arrête et on ouvre une issue.

**Hypothèses validées (2026-11-16).**

- Les trois BPMN de l'exemple partagent la clé `autorisationRiverain`. Chaque capsule déploie la variante dont elle a
  besoin ; l'exemple n'est pas modifié. (Au lot 3, C6 publiait elle-même sa variante ; la revue de clôture a reporté ce
  choix sur la scène.)
- En anglais, les pièces de C6 restent en français : ce sont des documents bruxellois réalistes, et seuls l'interface
  et les sous-titres sont traduits.

**Vérifié au lot 1.** La variante avec portail se déploie sur un serveur dont le portail est activé
(`RiverainDemoPackIT`, en français et en anglais).

**Lot 2 (C5 et S13).**

- **C5** se déroule en quatre temps :
  - une demande incomplète est encodée hors champ ;
  - l'agent demande une preuve de domicile ;
  - le lien personnel arrive dans Mailpit (boîte locale de la scène, vidée avant le tournage) ;
  - le demandeur répond sur un téléphone, sans session, et joint sa pièce ; la réponse et la pièce reviennent sur la
    fiche, et l'instruction reprend.
  Le plan « portail » est filmé à la taille d'un téléphone (`"device": "mobile"`, §140).
- **S13** est produit par le tournage de C5 : même lien, en haute densité, avant l'envoi de la réponse
  (`capture/out/<langue>/S13-portail-mobile.png`). Un screenshot à part devrait rejouer tout le parcours pour obtenir
  un lien.

**Défauts corrigés au tournage (lot 2).**

- **Portail sur mobile** : l'en-tête (logo, « Dossier DOS-AUTORISATION_RIVERAIN-… », langue) ne passait pas à la ligne
  et la référence ne se coupait pas. La page faisait 504 px de large pour un écran de 390. Le correctif est en CSS ;
  `E2E-PORTAL-8` vérifie qu'il n'y a pas de défilement horizontal à 390 px.
- **Consignes des tâches en anglais** : la démo anglaise traduisait les noms et les `<description>` des BPMN, mais pas
  les `<documentation>`. Ce sont les consignes affichées sur la fiche et sur le portail ; le portail anglais montrait
  donc une consigne en français. `DemoExample` les traduit désormais, `DemoTranslationsTest` les exige, et les douze
  consignes des trois exemples ont leur traduction dans `en/names.json`.

**Lot 3 (C6).**

- C6 se prépare hors champ, sous une attente coupée au montage :
  - trois demandes du jeu d'évaluation sont créées : conforme, preuve au nom d'un autre, consigne injectée. Chacune
    a sa plaque (`1-RIV-101` à `103`), car une seule demande peut être ouverte par plaque ; leurs pièces la reprennent.
- Le film montre la pré-vérification de chaque fiche, puis l'agent accepte le constat du troisième cas.
- Le tournage **échoue si le modèle ne signale pas l'écart** des deux derniers cas : les sous-titres l'affirment.
  `gemma4:12b` les a signalés en FR comme en EN.
- **Réglages de la scène pour un modèle local.** Le délai est porté à 600 s (`APP_PRECHECK_TIMEOUT_SECONDS`) : Ollama
  traite les demandes une à une. Le raisonnement du modèle est coupé (`APP_PRECHECK_REASONING_EFFORT=none`) : sinon
  `gemma4` épuise ses 2000 tokens de sortie à réfléchir, et la réponse coupée n'est pas un JSON valide. C'est le cas
  décrit dans DEPLOIEMENT, § pré-vérification.
- En anglais, les pièces et donc les constats du modèle restent en français (hypothèse validée) ; les sous-titres et
  l'interface sont en anglais.

**Revue de clôture (indépendante).**

- **Moyen, corrigé** : C6 publiait par l'API le BPMN brut de la variante avec pré-vérification ; en anglais, ses noms
  de tâches et ses consignes restaient en français. La scène déploie désormais la variante voulue
  (`app.demo.riverain.variant`, `APP_DEMO_RIVERAIN_VARIANT`), traduite par `DemoExample` ; `scripts/video.sh start
  c06-…` la demande, et le film ne publie plus rien. Test : `RiverainDemoPackIT`, variante de C6 en anglais.
- **Bas, corrigés** :
  - la garde du film de C6 vise le constat attendu par le jeu d'évaluation (question `domicileAuNom`) ;
  - `scripts/video.sh` lit `.env` avant les valeurs par défaut de la scène, qui ne l'écrasent plus ;
  - le gabarit de demande d'informations reprend un brouillon laissé par une installation interrompue ;
  - `DemoTranslationsTest` exige les mêmes expressions `${…}` dans `en/names.json` et dans les modèles HTML anglais ;
  - l'en-tête du portail ne passe à la ligne que sur un écran étroit : à 1280 px, une référence longue le faisait
    passer sur deux lignes (`E2E-PORTAL-9`).
