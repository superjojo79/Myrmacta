# §127 — Démo par paquets et captures pour le site

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] ouvert (issue [#55](https://github.com/superjojo79/Myrmacta/issues/55), vague `vague-vitrine`). Cadrage validé par
l'utilisateur le 2026-11-11.

**Constat.** Le site myrmacta.be a besoin de screenshots et de capsules vidéo (#56 à #59), à régénérer à chaque release
qui change l'UI. La seule démo reproductible, `demo-mailroom` ([§113](113-digital-mail-room-courrier-dossier-et-fils.md)),
ne couvre qu'un exemple. Les tests e2e Playwright simulent le backend et ne peuvent pas servir de captures.

**Décisions tranchées par l'utilisateur (2026-11-11).**

- **D-DEMO-PACKS** — un profil Spring `demo` composé de **paquets**, un par exemple (mail room, onboarding collaborateur,
  autorisation riverain) plus un paquet « états » (dossiers à différents stades, SLA dépassés, incident, brouillon MCP).
  Un orchestrateur installe, dans un ordre fixe, chaque paquet pas encore installé. `demo-mailroom` reste utilisable seul :
  il n'active que le paquet mail room. Écartés : étendre `MailRoomDemoSeeder` (un seeder qui mélange trois exemples) ;
  un script externe par l'API (jeton M2M, et certains états ne s'obtiennent pas par l'API).
- **D-DEMO-LOCALE** — une langue par démarrage (`APP_DEMO_LOCALE=fr|en`). Les fichiers d'exemple français restent la
  source ; un paquet lit, en anglais, une variante traduite placée à côté. On remet la base à zéro entre deux langues.
  Écarté : deux tenants FR et EN (provisioning et comptes Keycloak de plus).
- **D-DEMO-FAKE-APIS** — les faux SIRH, IAM et ITSM de l'onboarding sont embarqués dans le profil de démo : serveur HTTP du
  JDK sur la boucle locale, comme `OnboardingOrchestrationExampleIT`, sans nouvelle dépendance. Ils répondent avec succès ;
  les réponses en erreur viendront avec la capsule « robustesse » (#57). Écarté : WireMock en conteneur.
- **D-DEMO-CAPTURE** — les captures sont des scripts Playwright à part (`frontend/capture/`,
  `playwright.capture.config.ts`, `npm run capture`) qui pilotent une vraie instance de démo, avec la connexion Keycloak
  locale `demo` / `demo`. Hors `test:all` et hors CI. Écartés : les placer dans `frontend/e2e` ; un projet Node à la racine.
- **Périmètre de #55** : le socle et les 15 screenshots de #58, en FR et en EN. Les scripts des capsules vidéo restent dans
  #56 à #58.

**Décisions de conception (2026-11-11).**

- **D-DEMO-SCOPE** — le code de démo vit sous `src/embedded` (build `embedded-db` seulement, jamais dans le jar de
  production, contrôlé par `scripts/check-no-embedded-db-in-jar.sh`) et installe par les vrais cas d'usage. La garde
  SSRF n'est pas assouplie : `http` vers la boucle locale est déjà permis en profil `local`.
- **D-DEMO-BACKDATE** — le SLA se mesure depuis `createdAt`, posé à la création. Pour montrer des SLA dépassés et un
  historique de rapports, le paquet « états » antidate les dossiers qu'il a lui-même créés (`createdAt`, `closedAt`), par
  le port `DossierRepository`. C'est le seul écart aux cas d'usage, limité à la démo.
- **D-DEMO-CAPTURE-OUT** — la sortie des captures (`frontend/capture/out/`) n'est pas versionnée ; le site
  la reprend.

Document de travail : `docs/ai/2026-11-11-outillage-demo-captures.md` (supprimé à la clôture).
