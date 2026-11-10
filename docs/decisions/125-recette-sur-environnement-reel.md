# §125 — Recette sur environnement réel (ClamAV, Keycloak)

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-10). Issue #18.

**Constat.** Deux vérifications contre de vrais services ne tournaient jamais : `ClamAvAttachmentScanIT` (vrai
`clamd`, Testcontainers) s'ignorait sans démon Docker et n'avait jamais été exécuté ; la recette Keycloak
(`scripts/keycloak-realm-smoke.sh`, D-BEAUTH-KC-SMOKE) ne couvrait pas le client `client_credentials` par tenant que
la chaîne M2M accepte (`M2mTenantValidationFilter`, `M2mJwtAuthoritiesConverter`).

**Décisions tranchées par l'utilisateur (2026-11-10).**

- **Recette locale et job CI à la demande**, pas l'un ou l'autre. Les deux restent hors `./mvnw verify`
  (D-AV-CLAMAV-TEST-SCOPE : démarrage de `clamd` en minutes ; Keycloak téléchargé et démarré).
- **Garde explicite `-Dclamav.it=true` sur `ClamAvAttachmentScanIT`.** Depuis #157, surefire inclut les `*IT` :
  le test tournait dans chaque `./mvnw verify` dès qu'un démon Docker ou Podman répondait, contrairement à
  D-AV-CLAMAV-TEST-SCOPE, sans que ce soit tranché. Sans la propriété, le test est ignoré ; avec, un démon absent
  est un échec (le job CI ne peut plus passer au vert sur deux tests ignorés).
- **Workflow `workflow_dispatch`** (`.github/workflows/recette-env-reel.yml`) : un job ClamAV (Docker natif
  d'`ubuntu-latest`, jamais le runner auto-hébergé de [§110](110-runner-github-actions-auto-heberge.md), qui n'a pas
  de démon) et un job Keycloak (JDK 21, `vars.CI_RUNNER` comme les autres workflows). Pas de déclenchement sur
  `push` ni `pull_request`.
- **Recette M2M = ce qu'émet Keycloak**, pas l'application démarrée : token `client_credentials` du client
  `myrmacta-m2m-<tenant>`, claims `tenant_id`, `roles` (`API_SERVICE`) et `azp` (= `M2M_TRUSTED_CLIENT_IDS` du
  `.env` généré), idempotence du client et de ses mappers, refus du secret d'un autre tenant. Le traitement de ces
  claims côté Spring reste prouvé par les tests de la chaîne de sécurité, avec les mêmes noms de claims.
- **FAS et itsme réels hors de ce point** : la liste de recette attend un contrat (intégration BOSA, environnement
  de test itsme) et vit dans sa propre issue, rattachée à [§104](104-authentification-belge-fas-et-itsme.md).

**Défaut trouvé par la recette (corrigé).** Sous Git Bash (Windows), `new_uuid` de `provision-tenant.sh` ne
trouvait ni `/proc/sys/kernel/random/uuid` ni `uuidgen` et rendait une chaîne vide sans erreur : le flux
`browser-fournisseurs` et la surcharge du client `mon-app` recevaient l'identifiant `""`, que Keycloak ignore. En mode
`idp-only`, un compte ordinaire se connectait alors par mot de passe. `ProvisionTenantRealmScriptTest` comparait les
deux identifiants entre eux et passait. Correctif : repli sur `/dev/urandom`, arrêt du script si l'identifiant n'est
pas un UUID, et le test exige un UUID v4. Sur Linux (`/proc` présent), le défaut ne se produisait pas.

**Mise en œuvre.** Recette décrite dans `docs/guides/DEPLOIEMENT.md` §21 ; spécifications
[03-authentification.md](../specs/03-authentification.md) et [04-dossiers.md](../specs/04-dossiers.md).
