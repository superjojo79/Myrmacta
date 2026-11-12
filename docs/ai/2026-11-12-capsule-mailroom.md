# Capsule vidéo L1 « Digital Mail Room » — document de travail

Issue : #56 · Spec du domaine : docs/specs/47-postgresql-embarque-tests-demo.md (démo), docs/specs/49-*.md · Points du
registre : §127 (démo et captures, clos), §128 à créer (capsules vidéo)

## Cadrage

- Problème et utilisateurs : la page « Cas d'usage » de myrmacta.be a besoin d'une capsule de 4 à 5 min montrant la
  Digital Mail Room de bout en bout, en français et en anglais, régénérable à chaque release qui change l'interface.
- Périmètre :
  - tournage automatisé et rejouable (Playwright enregistre chaque chapitre contre une vraie démo, FR puis EN) ;
  - voix off de synthèse (edge-tts, voix neuronales Microsoft, fr-BE et en-GB) à partir d'un texte versionné ;
  - sous-titres VTT et chapitres dérivés du même texte ;
  - assemblage ffmpeg en une vidéo finale par langue (MP4, chapitres intégrés) ;
  - e-mails reçus en direct : serveur GreenMail (SMTP + IMAP) en conteneur Podman, relevé par le connecteur IMAP
    existant ; le tournage envoie la facture puis la réponse du fournisseur en SMTP ;
  - chapitre 3 : un client Python (Ollama `gemma4:12b`, endpoint compatible OpenAI ↔ serveur MCP M2M) joue l'agent
    pour de vrai ; son journal est rendu dans un panneau façon terminal, filmé à côté de Myrmacta.
- Hors périmètre : capsules courtes et onboarding (#57 à #59, qui réutiliseront l'outillage) ; hébergement des vidéos
  sur le site ; voix humaine ; musique.
- Critères de succès (vérifiables) :
  - une commande produit `l1-mailroom-fr.mp4` et `l1-mailroom-en.mp4` (4 à 5 min, chapitres, sous-titres VTT à côté)
    depuis une démo vide ;
  - les six chapitres de l'issue sont présents, chacun avec voix off synchronisée ;
  - le chapitre 3 montre un vrai appel des outils MCP par gemma4 (à blanc puis réel) ;
  - rien de l'outillage n'entre dans le jar de production ni dans `test:all` ;
  - la procédure est documentée (GETTING-STARTED) et relue sur une exécution réelle.
- Contraintes : outillage de développement seulement (Python via `uv run` avec dépendances en ligne, ffmpeg installé sur
  le poste, GreenMail en conteneur) ; une seule modification du produit, l'IMAP vers la boucle locale en profil local (§128) ; le texte de voix off part
  chez Microsoft (edge-tts) : texte marketing public, aucune donnée personnelle ; Windows + Podman.
- Validé par l'utilisateur le : 2026-11-12. Décisions : [§128](../decisions/128-capsules-video-du-site.md).

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Où travailler | worktree issue-55, nouveau, checkout principal | worktree issue-55 | tranchée (utilisateur) |
| Jusqu'où automatiser | tournage + textes, tout, storyboard | — | tranchée : tout automatiser (utilisateur) |
| Moteur de voix | Piper, edge-tts, Kokoro, SAPI | Piper | tranchée : edge-tts (utilisateur) |
| Montrer l'agent IA | script + panneau, client tiers, effet seul | script + panneau | tranchée (utilisateur) |
| E-mails en direct | GreenMail, déclencheur de démo, préinstallé | GreenMail | tranchée (utilisateur) → §128 D-VIDEO-MAIL |
| IMAP vers un GreenMail local, refusé par la garde | ouvrir en profil local, déclencheur de démo, préinstallé | ouvrir en profil local | tranchée (utilisateur) → §128 D-IMAP-LOCAL-LOOPBACK |
| Chapitre 5 (rappel J-5, escalade) | factures semées avec une échéance proche ou passée, horloge de démo | factures semées : le vrai job de l'échéance les traite, un carton « 25 jours plus tard » fait la transition | hypothèse → §128 D-VIDEO-DEADLINE |

## Conception

- Faits vérifiés :
  - `ImapInboundConnector.validate` et `poll` passent l'hôte à `OutboundUrlGuard.assertSafe("https://" + host)`, qui
    refuse toujours le HTTPS vers la boucle locale ou une adresse privée ; `security` n'accepte que SSL ou STARTTLS ;
    `ImapMailbox.Security.PLAIN_FOR_TESTS` existe, jamais produit par le connecteur. Le garde sait si le `http` local
    est permis (`allowLocalHttp`, profils `local` et `test`).
  - Attestation de l'expéditeur : premier en-tête `Authentication-Results` dont l'authserv-id vaut `trustedAuthservId`
    (`AuthenticationResults`) ; le tournage l'ajoute aux e-mails qu'il envoie.
  - Envoi SMTP : `spring.mail` (`MAIL_HOST`, `MAIL_PORT`) ; Mailpit est déjà dans `docker-compose.yml`.
  - MCP M2M : `/api/m2m/mcp`, JWT HS256 signé par `M2M_JWT_SECRET` permis en `local` ; l'acteur est le `sub` du JWT
    (`McpAccessContextFactory`) ; `list_my_tasks` voit les tâches des groupes de ce sujet, qui doit donc être membre de
    COURRIER et connu de l'annuaire.
  - Échéance : `DeadlineAlertJob` (`app.deadlines.scan-interval-ms`, 15 min par défaut ; délai initial 2 min) ; type
    FACTURE : `config/echeance-facture.json` (rappel J-5, escalade à DIRECTION_FINANCIERE le lendemain du terme).
  - Paquets de démo : `DemoPack` (ordre unique), `MailRoomDemoPack` installe l'exemple puis remet trois courriers.
- Composants :
  - produit : `ImapInboundConnector` (boucle locale en profil local/test, sécurité `PLAIN`) ;
  - `src/embedded` : profil `demo-video-mailroom` qui installe l'exemple mail room **sans** les trois courriers, crée
    le connecteur IMAP vers GreenMail, inscrit le sujet de l'agent dans l'annuaire et le groupe COURRIER, sème les
    factures de l'échéance ;
  - `docker-compose.yml` : service `greenmail` (profil compose `video`) ;
  - `scripts/video/` (Python, `uv run` avec dépendances en ligne) : envoi SMTP, agent MCP, voix edge-tts et VTT,
    assemblage ffmpeg ; `frontend/capture/` : tournage Playwright (`*.film.ts`, vidéo par chapitre) ;
  - texte de la capsule versionné : `docs/site/capsules/l1-mailroom.{fr,en}.yml` (chapitres, répliques, actions).
- Scénarios de test :
  - unitaires : validation IMAP (boucle locale permise en local, refusée sinon ; `PLAIN` refusé hors boucle locale) ;
  - intégration : `ImapInboundConnector` relève un GreenMail en mémoire sur la boucle locale en profil local (IT) ;
  - embedded : le paquet de la capsule s'installe (IT) ;
  - outillage : tests Python légers (découpage VTT, minutage) ; le tournage lui-même se prouve par une exécution réelle
    relue.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | Produit : IMAP vers la boucle locale en profil local/test (D-IMAP-LOCAL-LOOPBACK), tests, revue sécurité | en revue | #74 |
| 2 | Scène : GreenMail en compose, profil `demo-video-mailroom`, envoi SMTP scripté, arrivée réelle vérifiée | en revue | (cette PR) |
| 3 | Agent IA : client Python Ollama ↔ MCP, panneau terminal | à faire | |
| 4 | Tournage, voix, sous-titres, montage FR et EN ; guide | à faire | |

Prochaine action : lot 3 (agent IA : `scripts/video/agent.py`, panneau `frontend/capture/panel/agent-panel.html`).

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-12 | lot 1 : `./mvnw clean verify` | 4 908 tests, 0 échec |
| 2026-11-12 | lot 1 : `ImapInboundConnectorTest`, `OutboundUrlGuardTest`, `ImapLocalLoopbackTest` (GreenMail réel) | 138 tests, 0 échec |
| 2026-11-12 | lot 1 : `scripts/check-docs.sh` | tous les contrôles ok |
| 2026-11-12 | lot 2 : `./mvnw clean verify` | 4 908 tests, 0 échec |
| 2026-11-12 | lot 2 : tests embedded de la CI (dont `VideoMailRoomDemoPackIT`) | 32 tests, 0 échec |
| 2026-11-12 | lot 2 : jar de production sans démo, `scripts/check-docs.sh` | ok |
| 2026-11-12 | lot 2 : scène réelle (`scripts/video.sh mail-servers`, `start`, `mail facture`) | facture envoyée en SMTP, relevée par IMAP, accusé dans Mailpit 27 s plus tard |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
| Lot 1 (revue sécurité) : le profil `test` hors poste local ouvre la boucle locale (webhooks, IMAP) | bas | #73 |
| Lot 1 : connexion IMAP par le nom `localhost` (résolveur du poste) | bas | corrigé : adresse littérale `127.0.0.1` |
| Lot 1 : option `PLAIN` proposée dans tous les profils | bas | corrigé : proposée seulement en profil local ou test |
