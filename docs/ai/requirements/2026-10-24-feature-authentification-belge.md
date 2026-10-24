---
phase: requirements
title: Authentification belge (FAS, itsme) des agents et des tiers du portail
description: Issue #210 — brancher FAS (BOSA) et itsme comme fournisseurs d'identité Keycloak, par tenant, pour les agents et pour les tiers du portail externe
---

# Exigences — `authentification-belge` (issue #210)

## Problème

FlowForge ne propose aucune authentification belge : ni eID, ni itsme, ni CSAM. Le realm Keycloak n'a aucun
`identityProviders`. Les communes la mettent en tête de leurs cahiers des charges ; le marché de Schaerbeek (échéance
05/11/2026) exige itsme, eID ou CSAM.

État vérifié du code (2026-10-24) :

- **Agents.** Connexion OIDC au BFF (`SecurityConfig`, chaîne `@Order(5)`). L'identifiant FlowForge est le `sub` Keycloak
  (`AccessContextFactory`) : ACL par type, assignations, annuaire (`KnownUserRecordingSuccessHandler`). Le `tenant_id` et
  les rôles sont des attributs du compte, posés à la main par un admin (`DEPLOIEMENT.md` §6) ; `TenantResolutionFilter`
  refuse (403) un `tenant_id` absent ou différent de `app.tenant.id`.
- **Realm.** Un realm partagé par tous les tenants (`provision-tenant.sh`, `KEYCLOAK_REALM`, défaut `flowforge`), alors
  que chaque tenant a sa propre instance et son propre `OIDC_ISSUER_URI` (décision n° 13).
- **Tiers.** Le portail (§68) n'a qu'un lien magique : jeton opaque haché, sans session, aucune identité vérifiée.
  L'acteur est `AccessContext.External(portalLinkId)` et la trace `TaskQuestionAnswer.submittedBy = portal:<linkId>`.

Keycloak 26.7.1 sait déjà être client OIDC de FAS et d'itsme : `private_key_jwt` (signé avec la clé active du realm), JWE,
PKCE, URI de redirection fixe `/realms/<realm>/broker/<alias>/endpoint` (lecture des sources, commentaire de #210).

## Objectifs

1. **Agents.** Un agent se connecte à FlowForge par FAS (eID, itsme, eIDAS, via BOSA) ou par itsme en direct, depuis la
   page de connexion Keycloak de son tenant.
2. **Tiers du portail.** Une tâche externe peut exiger, en plus du lien magique, une authentification forte du tiers. Le
   NRN vérifié est comparé à celui attendu dans le dossier, ou y est écrit s'il n'est pas encore connu.
3. **Par tenant.** Chaque tenant active ses fournisseurs avec ses propres identifiants et clés : chaque contrat FAS ou
   itsme est propre à une commune.
4. **Démontrable sans contrat.** Un fournisseur simulé permet les tests automatisés et la démonstration locale.

## Non-objectifs

- Dépôt d'une demande initiale par un citoyen authentifié (#188, à arbitrer).
- Authentification forte des signataires du portail de signature (§72, #191) : le fournisseur de signature identifie.
- Extension Java de Keycloak (SPI d'authentification ou de mapper).
- Gestion des comptes dans l'interface FlowForge (toujours dans Keycloak, `DEPLOIEMENT.md` §6).
- Migration automatique d'un déploiement existant du realm partagé vers un realm par tenant (procédure documentée).
- Recette contre les vrais FAS et itsme : ajoutée à #192, faite dès qu'un contrat existe.
- Contrôle du niveau d'authentification des agents par FlowForge : la politique du realm s'applique.

## Récits utilisateur

- **Agent communal** : je clique « itsme » ou « eID / FAS » sur la page de connexion de ma commune et j'arrive dans
  FlowForge, sans mot de passe à retenir.
- **Agent qui a déjà un compte** : à ma première connexion par FAS, je relie ce moyen à mon compte existant en me
  ré-authentifiant une fois ; je garde mes droits, mes tâches et mes dossiers suivis.
- **Administrateur du tenant** : un nouvel agent qui s'est connecté par FAS apparaît dans Keycloak sans aucun droit ; je
  lui attribue ses rôles, comme pour un compte créé à la main.
- **Commune exigeante** : j'impose l'authentification forte à mes agents, en gardant un compte admin de secours à mot de
  passe.
- **Tiers (citoyen, entreprise)** : je reçois le lien d'une tâche ; avant de répondre, je m'identifie par itsme ou eID.
  Si je ne suis pas la personne attendue, ma réponse est refusée.
- **Concepteur de workflow** : je marque une tâche externe « authentification forte » et je désigne le champ du dossier
  qui porte le NRN du demandeur ; s'il est vide, la première authentification le remplit.
- **Instructeur** : je vois dans la trace de la réponse que le tiers s'est authentifié, par quel moyen, à quel niveau,
  et que son identité correspond au dossier.

## Règles fonctionnelles

### Realm et fournisseurs (K)

| # | Règle |
|---|---|
| K1 | Modèle de production par défaut : **un realm Keycloak par tenant**. Le provisionnement génère ce realm : client BFF, quatre rôles, profil utilisateur (`tenant_id` réservé à l'admin), fournisseurs choisis. Le realm partagé reste possible (dev, déploiements existants). |
| K2 | Deux fournisseurs, activables séparément par tenant : **FAS** (BOSA : eID, itsme, eIDAS, code à usage unique) et **itsme en direct** (Belgian Mobile ID). Chacun avec ses propres identifiants, métadonnées et clés. |
| K3 | itsme impose `private_key_jwt` : l'assertion est signée avec la clé du realm du tenant, dont la JWKS est publiée par Keycloak. |
| K4 | Un fournisseur simulé, lancé par `docker-compose`, émet les mêmes claims que FAS et itsme et exige `private_key_jwt`. Le realm de dev et le portail de dev l'utilisent. |
| K5 | Noms des claims (NRN, prénom, nom, niveau) et correspondance des valeurs de niveau : réglables par fournisseur, avec des valeurs par défaut documentées, à confirmer lors de la recette réelle (#192). |

### Agents (A)

| # | Règle |
|---|---|
| A1 | La page de connexion du realm du tenant propose les fournisseurs activés. |
| A2 | Première connexion par un fournisseur sans compte relié : Keycloak crée le compte **sans `tenant_id` ni rôle**. FlowForge le refuse (403, `TenantResolutionFilter`) jusqu'à ce qu'un admin pose le `tenant_id` et les rôles. |
| A3 | Un agent qui a déjà un compte peut y relier le fournisseur en se ré-authentifiant (flux Keycloak standard). Le `sub` reste le même, donc ses ACL, assignations et entrées d'annuaire aussi. |
| A4 | Connexion par mot de passe réglable par tenant : mot de passe et fournisseurs côte à côte (défaut), ou fournisseurs seuls. En mode « fournisseurs seuls », les comptes de secours (rôle `secours`) entrent encore dans FlowForge par mot de passe et OTP Keycloak. Sans `--realm-per-tenant`, rien ne change. |
| A5 | Le NRN d'un agent n'est jamais transmis à FlowForge : aucun mapper ne le pose dans les jetons du client BFF. |
| A6 | Un utilisateur dont le `tenant_id` est absent ou différent de celui de l'instance n'entre jamais dans l'annuaire des assignés, et n'obtient aucune session. |

### Tiers du portail (T)

| # | Règle |
|---|---|
| T1 | Une tâche externe (`user-question`, §68) peut exiger l'authentification forte, par un attribut BPMN littéral (jamais évalué en UEL, §69). |
| T2 | Elle désigne alors le **champ d'identité** du dossier : `dossierField:<champ>`, de type `STRING` et `sensitive` dans le type du dossier. Vérifié à l'émission du lien, puisque le type peut changer après le déploiement. Un champ inutilisable donne un lien `NOT_SENT` (raison `INVALID_IDENTITY_FIELD`) et la notification des participants, comme une adresse absente (D-PORTAL-NO-ADDRESS) : jamais d'incident. |
| T3 | Le tiers ouvre le lien, puis s'authentifie par un fournisseur activé. L'instance FlowForge est **elle-même cliente OIDC** de FAS et d'itsme pour le portail, sans Keycloak : aucun compte n'est créé pour un tiers. |
| T3b | Avant l'authentification, le porteur du lien ne voit que le nom de la tâche, la référence du dossier et le choix du fournisseur : ni instructions, ni formulaire, ni pièces. Dépôt de pièce et soumission sont refusés sans identité conforme. |
| T4 | Niveaux acceptés : eIDAS **substantiel** ou **élevé** (eID, itsme, eIDAS étranger). Un niveau faible (code à usage unique) est refusé. |
| T5 | Champ d'identité **rempli** : le NRN vérifié doit lui être égal (forme normalisée : 11 chiffres). Sinon refus neutre. |
| T6 | Champ d'identité **vide** : le NRN vérifié y est écrit **à la soumission**, dans la transaction de la complétion. Si le champ a été rempli entre-temps avec un autre NRN, refus neutre. |
| T7 | Identité sans NRN (eIDAS étranger) : refus si un NRN est attendu ; si le champ est vide, rien n'est écrit et seul le nom est tracé. |
| T8 | La trace de la réponse conserve le nom vérifié, le fournisseur, le niveau et le résultat (« conforme », « premier enregistrement », « sans NRN »), jamais le NRN. |
| T9 | L'identité vérifiée vaut pour **ce lien et cet appareil seulement**, 60 minutes par défaut (réglable, plafond 4 h). Elle tombe à la soumission, à la révocation, à l'expiration du lien et à la purge du dossier. Un lien renvoyé exige une nouvelle authentification. |
| T10 | Un refus (identité non conforme, niveau insuffisant, authentification annulée) ne révèle pas le NRN attendu : même refus neutre que le portail. |
| T11 | La clé de l'instance pour `private_key_jwt` (et le déchiffrement d'un jeton chiffré) vient de la configuration, jamais de la base ; sa partie publique est publiée en JWKS à une URL publique de l'instance, enregistrée chez le fournisseur. |

### NRN (N)

| # | Règle |
|---|---|
| N1 | Le NRN n'est écrit que dans un champ `sensitive` : le masquage existant s'applique (API, exports, webhooks, MCP). |
| N2 | Le NRN n'apparaît dans aucun log, aucune trace d'audit, aucune ligne d'outbox, aucune variable de process. |
| N3 | Normalisation : séparateurs retirés ; 11 chiffres exactement, sinon le NRN est traité comme absent (T7). |

### Activation (E)

| # | Règle |
|---|---|
| E1 | Réglage d'instance, **désactivé par défaut**, qui autorise l'authentification forte des tiers et l'usage du NRN. Il suppose le portail activé. L'autorisation du Registre national revient à la commune (guide de déploiement). |
| E2 | Un BPMN dont une tâche exige l'authentification forte est **refusé au déploiement** si le réglage est éteint ou si aucun fournisseur n'est configuré pour les tiers. |

## Critères d'acceptation

- [ ] Le provisionnement génère le realm d'un tenant (client BFF, rôles, profil, fournisseurs choisis) et le script
      Keycloak correspondant, idempotent.
- [ ] Un agent se connecte par le fournisseur simulé : compte créé sans `tenant_id` ni rôle, FlowForge le refuse (403)
      et ne l'inscrit pas dans l'annuaire ; une fois validé par un admin, il travaille normalement.
- [ ] Un utilisateur d'un autre tenant qui se connecte à l'instance n'entre pas dans l'annuaire (A6, défaut existant).
- [ ] Un agent qui a déjà un compte relie le fournisseur simulé et garde son `sub` (ACL et assignations intactes).
- [ ] Un tenant en mode « fournisseurs seuls » n'affiche plus le formulaire de mot de passe ; le compte de secours reste
      utilisable.
- [ ] Le NRN d'un agent n'est dans aucun jeton reçu par le BFF.
- [ ] Tâche à authentification forte, champ rempli : un NRN conforme permet la soumission ; un autre NRN, un niveau
      faible ou une authentification annulée donnent un refus neutre.
- [ ] Tâche à authentification forte, champ vide : le NRN vérifié est écrit à la soumission et masqué ensuite.
- [ ] Identité eIDAS sans NRN : refusée si un NRN est attendu, acceptée sans écriture sinon.
- [ ] Le NRN est absent des logs, de l'audit, de l'outbox, des variables de process et des webhooks (test dédié).
- [ ] Déploiement refusé pour une tâche à authentification forte quand le réglage est éteint ou sans fournisseur.
- [ ] Champ d'identité absent, non `STRING` ou non sensible à l'émission : lien `NOT_SENT`
      (`INVALID_IDENTITY_FIELD`), participants notifiés, process sans incident.
- [ ] Avant l'authentification, la page portail ne montre que le nom de la tâche et la référence ; dépôt de pièce et
      soumission refusés (T3b).
- [ ] Aucun compte Keycloak n'est créé pour un tiers ; la JWKS de l'instance est publiée et le simulateur l'utilise
      pour vérifier `private_key_jwt` (T11).
- [ ] Portail (Vitest et Playwright) : écran d'authentification, retour du fournisseur, refus neutre, soumission.
- [ ] `./mvnw verify`, `cd frontend && npm run lint && npm run test:all` et `scripts/check-docs.sh` verts.

## Contraintes

- Échéance du marché de Schaerbeek : 05/11/2026. Livrer d'abord ce qui est démontrable (agents, simulateur).
- Architecture hexagonale (ArchUnit) : la règle de comparaison et de normalisation du NRN vit dans le domaine ; le
  protocole OIDC est un détail d'infrastructure.
- Le portail reste sans session (D-PORTAL-CSRF-SESSION) et sa chaîne de sécurité reste conditionnelle (§68).
- Keycloak 26.7.1 (version de `docker-compose.yml`), sans extension. Spring Security 7 côté application.
- Schéma par tenant : nouveau changeSet Liquibase si une table est nécessaire, jamais de modification d'un changeSet
  existant.
- Pas de CI GitHub Actions (#219) : les vérifications locales font foi.

## Hypothèses validées (cadrage du 2026-10-24)

Tranché par l'utilisateur : public (agents et tiers), fournisseurs (FAS et itsme en direct), un realm par tenant,
première connexion d'un agent (compte sans rôle, rattachement par ré-authentification), mot de passe réglable par tenant,
identité attendue vérifiée pour les tiers, NRN posé dans le dossier par un champ cible unique (vide : écrit ; rempli :
attendu), niveaux substantiel ou élevé, activation opt-in avec refus au déploiement, validation sur simulateur et recette
réelle différée (#192).

Posé en cadrage et non contesté en phase 2 : A5 (NRN des agents jamais transmis), N2, N3, T8 (contenu de la trace), T9
(durée bornée).

## Revue de phase 2 (2026-10-24)

Tranché par l'utilisateur :

- **A2 révisée** — le compte créé à la première connexion n'a ni `tenant_id` ni rôle : un inconnu titulaire d'une eID
  n'entre pas dans l'application. Écarté : compte avec `tenant_id` sans rôle (il entrait, sans droit, et alimentait
  l'annuaire) ; compte désactivé (flux Keycloak spécifique).
- **T3 révisée** — le portail est client OIDC **direct** de FAS et d'itsme : aucun compte pour un tiers, clé propre à
  l'instance. Écarté : realm « portail » avec utilisateurs transitoires (fonction expérimentale de Keycloak depuis la
  version 23, statut GA non confirmé) ; realm « portail » avec comptes purgés (identités stockées entre deux purges).
  Conséquence : chaque fournisseur demande deux clients enregistrés par tenant (Keycloak pour les agents, l'instance pour
  le portail).
- **T3b ajoutée** — avant l'authentification : nom de la tâche et référence du dossier seulement. Écarté : écran
  totalement neutre ; contexte visible avec actions bloquées.
- **T2 précisée** — champ d'identité inutilisable : lien `NOT_SENT` et notification, comme une adresse absente. Écarté :
  incident rejouable.

Constats vérifiés dans le code, et corrections apportées :

- **A6 ajoutée (défaut existant)** — `KnownUserRecordingSuccessHandler` inscrit l'utilisateur dans l'annuaire au retour
  de connexion, avant toute vérification du `tenant_id` : `TenantResolutionFilter` (`SecurityConfig`, après
  `SecurityContextHolderFilter`) ne s'applique qu'aux requêtes suivantes. Aujourd'hui, un utilisateur d'un autre tenant
  du realm partagé qui se connecte à l'instance entre dans son annuaire ; demain, un compte sans `tenant_id` y entrerait
  aussi. Corrigé dans ce lot.
- **T11 ajoutée** — conséquence du client direct : clé `private_key_jwt` de l'instance et JWKS publique.
- **K5 ajoutée** — les noms des claims et des niveaux de FAS et d'itsme ne sont confirmés par aucune source publique
  trouvée (recherche du 2026-10-24 : l'attribut NRN d'itsme est `BENationalNumber` chez un intégrateur, le niveau FAS
  n'est pas documenté publiquement). Ils sont réglables, et la recette réelle (#192) les confirme.
- **T10 ajustée** — la référence du dossier étant visible avant l'authentification (T3b), le refus neutre ne protège
  plus que le NRN attendu.
- Le portail de signature (§72) reste hors périmètre : `SignaturePortalController` utilise un autre jeton.
- Le lint ai-devkit attend une branche `feature-authentification-belge` : écart assumé, la session impose la branche
  `ccr-1b2a2fd0-r71qj0`.

## Questions ouvertes

Aucune. Les points de conception ont été tranchés en phase 3 (voir la conception, « Décisions de la phase 3 »).
