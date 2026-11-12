# §128 — Capsules vidéo du site : tournage, voix et montage automatisés

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-12, issue [#56](https://github.com/superjojo79/Myrmacta/issues/56), vague `vague-vitrine`). Cadrage
validé par l'utilisateur le 2026-11-12. Livré en quatre lots (PR #74, #75, #76, #78) : IMAP vers la boucle locale en
profil local, scène de tournage (GreenMail, profil `demo-video-mailroom`), agent IA (gemma4 local et serveur MCP),
voix, tournage et montage. `scripts/video.sh capsule` produit la capsule L1 en français et en anglais (2 min 50, sept
chapitres, sous-titres) : plus courte que les 4 à 5 min visées, ce que l'utilisateur a accepté tant qu'elle est
complète. Comportement : [spec 47](../specs/47-postgresql-embarque-tests-demo.md) ; procédure :
[GETTING-STARTED](../guides/GETTING-STARTED.md) § « Capsule vidéo ». Suites : défauts visibles dans les vidéos #66,
#69, #79 ; constat de sécurité sur le profil `test` #73. Les capsules #57 à #59 réutiliseront cet outillage.

**Constat.** La page « Cas d'usage » de myrmacta.be attend une capsule de 4 à 5 min sur la Digital Mail Room, en français
et en anglais, à régénérer quand l'interface change. La démo et les captures de [§127](127-demo-par-paquets-et-captures-du-site.md)
donnent des écrans figés ; il faut des actions filmées, une voix off, des sous-titres et un montage, sans travail manuel.

**Décisions tranchées par l'utilisateur (2026-11-12).**

- **D-VIDEO-PIPELINE** — tout est automatisé et rejouable : Playwright filme chaque chapitre contre une vraie démo,
  une voix de synthèse lit un texte versionné, ffmpeg assemble image, voix, sous-titres et chapitres en un MP4 par
  langue. Outillage de développement : hors jar de production, hors `test:all` et hors CI.
- **D-VIDEO-TTS** — voix off par edge-tts (voix neuronales du service en ligne de Microsoft Edge, `fr-BE` et `en-GB`).
  Le texte de la voix off part chez Microsoft : c'est un texte public, sans donnée personnelle. Usage non officiel du
  service : s'il casse, on change de moteur sans toucher au reste (le texte et le minutage restent).
- **D-VIDEO-AGENT** — le chapitre « agent IA » est joué pour de vrai : un client Python relie un modèle local (Ollama,
  `gemma4:12b`, endpoint compatible OpenAI) au serveur MCP M2M ; son journal (outils appelés, à blanc puis réel) est
  rendu dans un panneau façon terminal, filmé à côté de Myrmacta.
- **D-VIDEO-MAIL** — les e-mails arrivent en direct par le vrai chemin : un serveur GreenMail (SMTP + IMAP) en
  conteneur, relevé par le connecteur IMAP de Myrmacta ; le tournage envoie la facture puis la réponse du fournisseur
  en SMTP. Les e-mails que Myrmacta envoie (accusé de réception, rappel) partent vers Mailpit, filmable.
- **D-IMAP-LOCAL-LOOPBACK** — le connecteur IMAP refusait tout hôte de boucle locale ou privé, dans tous les profils.
  En profils `local` et `test` seulement, il accepte désormais un hôte de boucle locale (`localhost` ou `127.x` ; une adresse IPv6
  n'est pas un nom d'hôte IMAP accepté), avec la sécurité `PLAIN` (sans chiffrement) permise pour ce seul cas : même règle que le `http` vers la boucle
  locale déjà permis aux webhooks (A13). Hors de ces profils, rien ne change : hôte public et SSL ou STARTTLS exigés.
  Ce changement nuance D-DEMO-SCOPE (« garde SSRF inchangée ») pour l'IMAP seulement.

**Hypothèse confirmée au tournage.**

- **D-VIDEO-DEADLINE** — le rappel J-5 et l'escalade de l'échéance ne se filment pas en direct (l'horloge ne s'avance
  qu'en test) : la démo de la capsule sème des factures dont l'échéance est proche ou passée, que le vrai job des
  échéances traite ; un carton « 25 jours plus tard » fait la transition. Le mécanisme est vérifié par `VideoMailRoomDemoPackIT` (un
  rappel, une escalade avec la tâche réaffectée). Au tournage, le rappel (une notification aux participants du dossier,
  pas au compte de démo) ne se montre pas : le chapitre montre l'échéance proche, puis la note d'escalade et la tâche
  passée à la direction financière.

Document de travail `docs/ai/2026-11-12-capsule-mailroom.md` : supprimé à la clôture.
