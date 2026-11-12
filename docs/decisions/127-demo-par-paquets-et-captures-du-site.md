# §127 — Démo par paquets et captures pour le site

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-12, issue [#55](https://github.com/superjojo79/Myrmacta/issues/55), vague `vague-vitrine`). Cadrage
validé par l'utilisateur le 2026-11-11. Livré en cinq lots (PR #60, #61, #62, #63, #65) : profil `demo` par paquets,
faux SIRH, IAM et ITSM, paquet « états », démo en anglais, 14 captures en français et en anglais. Comportement :
[spec 47](../specs/47-postgresql-embarque-tests-demo.md) § « Démo par paquets » ; procédure :
[GETTING-STARTED](../guides/GETTING-STARTED.md) § « Captures du site ». Défauts produit relevés par la démo : #66 à #70 ;
constats bas de la revue de clôture : #71. Capsules vidéo : #56 à #59.

**Constat.** Le site myrmacta.be a besoin de screenshots et de capsules vidéo (#56 à #59), à régénérer à chaque release
qui change l'UI. La seule démo reproductible, `demo-mailroom` ([§113](113-digital-mail-room-courrier-dossier-et-fils.md)),
ne couvre qu'un exemple. Les tests e2e Playwright simulent le backend et ne peuvent pas servir de captures.

**Décisions tranchées par l'utilisateur (2026-11-11).**

- **D-DEMO-PACKS** — un profil Spring `demo` composé de **paquets**, un par exemple (mail room, onboarding collaborateur)
  plus un paquet « états » (dossiers à différents stades, SLA dépassés, incident, brouillon MCP).
  Un orchestrateur installe, dans un ordre fixe, chaque paquet pas encore installé. `demo-mailroom` reste utilisable seul :
  il n'active que le paquet mail room. Écartés : étendre `MailRoomDemoSeeder` (un seeder qui mélange plusieurs exemples) ;
  un script externe par l'API (jeton M2M, et certains états ne s'obtiennent pas par l'API).
- **D-DEMO-LOCALE** — une langue par démarrage (`APP_DEMO_LOCALE=fr|en`). Les fichiers d'exemple français restent la
  source ; un paquet lit, en anglais, une variante traduite placée à côté. On remet la base à zéro entre deux langues.
  Écarté : deux tenants FR et EN (provisioning et comptes Keycloak de plus).
- **D-DEMO-FAKE-APIS** — les faux SIRH, IAM et ITSM de l'onboarding sont embarqués dans le profil de démo : serveur HTTP du
  JDK sur la boucle locale, comme `OnboardingOrchestrationExampleIT`, sans nouvelle dépendance. Ils répondent avec succès,
  sauf le SIRH, qui coupe la connexion pour une adresse personnelle en `.invalid` (domaine réservé) : c'est l'incident
  rejouable du paquet « états ». Les réponses en erreur viendront avec la capsule « robustesse » (#57). Écarté : WireMock en conteneur.
- **D-DEMO-CAPTURE** — les captures sont des scripts Playwright à part (`frontend/capture/`,
  `playwright.capture.config.ts`, `npm run capture`) qui pilotent une vraie instance de démo, avec la connexion Keycloak
  locale `demo` / `demo`. Hors `test:all` et hors CI. Écartés : les placer dans `frontend/e2e` ; un projet Node à la racine.
- **Périmètre de #55** : le socle et 14 des 15 screenshots de #58, en FR et en EN. L'exemple autorisation riverain
  est retiré (utilisateur, 2026-11-11) : pas de paquet riverain, donc pas de screenshot S13 (portail usager), et les
  capsules C5 et C6 de #58 n'ont pas de données dans la démo. Les scripts des capsules vidéo restent dans
  #56 à #58.

**Décisions de conception (2026-11-11).**

- **D-DEMO-SCOPE** — le code de démo vit sous `src/embedded` (build `embedded-db` seulement, jamais dans le jar de
  production, contrôlé par `scripts/check-no-embedded-db-in-jar.sh`) et installe par les vrais cas d'usage. La garde
  SSRF n'est pas assouplie : `http` vers la boucle locale est déjà permis en profil `local`.
- **D-DEMO-BACKDATE** — le SLA se mesure depuis `createdAt`, posé à la création. Pour montrer des SLA dépassés et un
  historique de rapports, le paquet « états » antidate les dossiers qu'il a lui-même créés (`createdAt`, `closedAt`), par
  le port `DossierRepository`. C'est le seul écart aux cas d'usage, limité à la démo.
- **D-DEMO-LOCALE-FILES** — forme de la variante anglaise (lot 2) :
  - fichiers JSON : une copie traduite à côté, au même chemin sous `en/` (`exemples/<ex>/en/config/…`). Seules les
    valeurs des clés de texte visible changent (`label`, `helpText`, `group`, `subject`, `textBody`, `htmlBody`,
    les commentaires `_…`, et `name` pour un connecteur seulement : ailleurs c'est un code) ; codes, clés et structure restent identiques, ce que vérifie
    `DemoTranslationsTest` à chaque `verify` ;
  - BPMN et DMN : pas de copie (un processus généré de 45 Ko dériverait). `en/names.json` traduit, texte français exact
    vers texte anglais, les attributs `name` et `label` et les éléments `description` ; le test exige une entrée pour
    chacun. Le `name` d'un élément qui le lit comme un code (message, signal, sortie ou donnée d'entrée DMN, élément
    `flowable:…`) n'est jamais traduit et n'a pas d'entrée : le renommer casserait le routage en anglais seulement
    (revue de clôture) ;
  - les textes écrits dans les paquets (objets des courriers, fonctions des arrivées, libellés) ont leurs deux langues
    dans le code du paquet ;
  - `scripts/demo.sh` passe aussi `APP_I18N_DEFAULT_LOCALE` à la langue de la démo.
- **D-DEMO-MEMBERS** (lot 3) — à sa connexion, le compte de démo (`app.demo.group-members`, `demo` par défaut) rejoint
  tous les groupes de la démo, pour que sa page « Tâches » montre les files des groupes. Un membre est identifié par son
  sujet OIDC, que Keycloak génère : la démo ne peut pas l'ajouter à l'installation. L'événement d'authentification
  précède l'inscription dans l'annuaire (D-ASSIGN-DIR) ; l'écouteur inscrit donc d'abord le compte, par le même cas
  d'usage et après le même contrôle de tenant. Écarté : des groupes Keycloak et un mapping `idpGroup`, que la plateforme
  enregistre sans s'en servir pour l'appartenance.
- **Jobs asynchrones des paquets** (lot 3) — avec l'exécuteur asynchrone actif (démo réelle), un paquet attend seulement
  que le processus atteigne l'étape voulue : exécuter aussi les jobs lui-même les fait se bloquer sur les verrous du
  dossier. Il ne les exécute que si l'exécuteur est coupé (les tests).
- **Brouillon DMN en 1.3** (lot 3) — l'éditeur intégré (dmn-js) n'ouvre que le DMN 1.3, alors que la table de routage
  de l'exemple est en DMN 1.1. Le brouillon MCP du paquet « états » passe la table en DMN 1.3, que le moteur accepte
  aussi. Le défaut de l'exemple et de la grille intégrée relève d'une issue à part.
- **D-DEMO-CAPTURE-OUT** — la sortie des captures (`frontend/capture/out/`) n'est pas versionnée ; le site
  la reprend.

Document de travail `docs/ai/2026-11-11-outillage-demo-captures.md` : supprimé à la clôture.
