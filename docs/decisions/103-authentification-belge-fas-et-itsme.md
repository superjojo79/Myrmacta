# §103 — Authentification belge (FAS, itsme) des agents et des tiers du portail

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] en cours (issue [#210](https://github.com/superjojo79/FlowForge/issues/210)). Lot A (Keycloak, agents) livré ;
restent le portail (lots B et C) et la documentation (lot D).

**Constat.** FlowForge ne proposait ni eID, ni itsme, ni CSAM ; le realm Keycloak n'avait aucun fournisseur d'identité.
Les communes l'exigent en premier (marché de Schaerbeek, échéance 05/11/2026). Les agents se connectent au BFF par OIDC
(Keycloak) ; les tiers du portail n'ont qu'un lien magique (§68).

**Décisions tranchées par l'utilisateur (phases 1 à 3, 2026-10-24).**

- **D-BEAUTH-AUDIENCE** — agents et tiers du portail dans la même feature. Écarté : agents seuls ; citoyens qui déposent
  une demande (#188).
- **D-BEAUTH-PROVIDERS** — FAS (BOSA : eID, itsme, eIDAS) et itsme en direct, activables par tenant. Écarté : FAS seul ;
  fournisseur OIDC générique sans gabarit.
- **D-BEAUTH-REALM** — un realm Keycloak par tenant (`flowforge-<tenant>`), modèle par défaut en production : clés,
  page de connexion et comptes propres. Le realm partagé reste possible. Écarté : realm partagé avec un alias de
  fournisseur par tenant (clés communes à toutes les communes).
- **D-BEAUTH-FIRST-LOGIN** — première connexion par un fournisseur : compte créé **sans `tenant_id` ni rôle**, refusé
  par FlowForge (403) jusqu'à validation par un admin ; rattachement à un compte existant par ré-authentification (même
  `sub`). Écarté : compte avec `tenant_id` sans rôle (un inconnu entrait, sans droit) ; pré-enregistrement par NRN
  (extension Keycloak) ; compte désactivé.
- **D-BEAUTH-PASSWORD** — connexion par mot de passe réglable par tenant : `both` (défaut) ou `idp-only`. Sans
  `--realm-per-tenant`, rien ne change.
- **D-BEAUTH-IDP-ONLY** — en `idp-only`, flux dédié au client BFF : fournisseurs pour tous, mot de passe et OTP pour les
  seuls comptes de rôle `secours` (qui administrent FlowForge si FAS est en panne). Écarté : comptes sans mot de passe
  (simple convention) ; redirection automatique (incompatible avec deux fournisseurs) ; aucun mot de passe vers
  FlowForge. Écart accepté : l'identifiant saisi révèle si le compte est un compte de secours.
- **D-BEAUTH-PROVISION** — `provision-tenant.sh --realm-per-tenant` écrit `<tenant>.realm.json` (déclaratif, sans
  secret) et `<tenant>.keycloak.sh` (idempotent : création, ou import partiel qui conserve l'existant ; secrets et URL
  des fournisseurs depuis l'environnement). Écarté : `kcadm` seul ; keycloak-config-cli.
- **D-BEAUTH-KC-SMOKE** — preuve sur un vrai Keycloak : `scripts/keycloak-realm-smoke.sh` (distribution zip, sans
  Docker), plus des tests de structure du JSON. Écarté : IT Testcontainers (jamais exécuté sans Docker, #219).
- **D-BEAUTH-VALIDATION** — critère de fin : tests et démo contre un fournisseur simulé (`fas-simulateur`) ; recette contre
  les vrais FAS et itsme dans #192.

Décisions du portail (lots B et C) : D-BEAUTH-PORTAL-CLIENT (client OIDC direct, aucun compte pour un tiers),
D-BEAUTH-PREVIEW (nom de la tâche et référence seulement avant l'authentification), D-BEAUTH-NRN-FIELD (champ cible
`STRING` sensible : vide, le NRN y est écrit ; rempli, il est attendu), D-BEAUTH-LOA (substantiel ou élevé),
D-BEAUTH-ACTIVATION (opt-in, refus au déploiement), D-BEAUTH-STORE, D-BEAUTH-TTL (60 min), D-BEAUTH-PROVIDER-CHOICE,
D-BEAUTH-PROOF. Détail dans la conception de la feature, reporté ici à la livraison.

**Défaut existant corrigé (A6).** `KnownUserRecordingSuccessHandler` inscrivait l'utilisateur dans l'annuaire des
assignés au retour de connexion, avant toute vérification du `tenant_id` (`TenantResolutionFilter` ne voit que les
requêtes suivantes) : un utilisateur d'un autre tenant du realm partagé entrait dans l'annuaire de l'instance. La règle
est partagée (`TenantMembership`) ; le gestionnaire ne l'inscrit plus, ferme sa session et répond 403.

**Lot A — preuves.** `KnownUserRecordingSuccessHandlerTest` (rouge si l'on retire le contrôle) ;
`ProvisionTenantRealmScriptTest` (structure) ; `scripts/keycloak-realm-smoke.sh` sur Keycloak 26.7.1 : deux applications
successives du script sans erreur, agent connecté par le FAS simulé (`private_key_jwt` signé par la clé du realm),
compte sans `tenant_id`, sans NRN, sans rôle FlowForge ; connexion directe par mot de passe en `both` ; mot de passe
refusé à un compte ordinaire en `idp-only` (`access_denied`) ; compte de secours : mot de passe temporaire changé, OTP
enrôlé, reconnexion par mot de passe et OTP.

**Constats de la recette Keycloak.** Keycloak refuse un fournisseur qui valide les signatures sans URL JWKS : le JSON
n'active la validation qu'avec les URL posées par le script (découverte). Le client BFF généré n'exige pas PKCE, comme le
client du realm de dev : rien ne garantit que le BFF l'envoie.

**Impacte :** `docs/specs/03-authentification.md`, `docs/specs/02-multi-tenant.md`, `docs/specs/26-portail-externe.md`,
`docs/guides/DEPLOIEMENT.md`, `docs/guides/GUIDE-CONCEPTION-BPMN.md`, `docs/guides/GUIDE-UTILISATEUR.md`.
