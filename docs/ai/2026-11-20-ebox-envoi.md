# Envoi vers l'eBox citoyen et entreprise — document de travail

Issue : #13 (sous #14) · Spec du domaine : [spec 06](../specs/06-connecteurs.md) (à compléter à la clôture) · Points du
registre : [§155](../decisions/155-envoi-ebox-citoyen-et-entreprise.md), [§150](../decisions/150-lecture-ebox-enterprise.md)

## Cadrage

- Problème et utilisateurs : un agent communal publie depuis un dossier une décision ou un courrier dans l'eBox d'une
  entreprise (BCE) ou d'un citoyen (NISS), au nom de la commune ; quand l'eBox n'est pas utilisable, le process suit une voie
  de secours (papier, e-mail). BOSA doit pouvoir valider la procédure sur la base d'un audit trail.
- Périmètre : modèle commun (format entreprise) + extension citoyen ; deux voies derrière un port : entreprise (eBox
  Enterprise, publication, OAuth v5 avec JWT signé par certificat X.509) et citoyen (BOSA, autres endpoints, OAuth sans X.509,
  API inconnue : stub générique) ; consentement par voie avec voie de secours ; recommandé et preuve de dépôt (Registered
  Mail 1.1) ; journal d'audit non modifiable inspiré d'ATREQ ; tests contre un faux serveur uniquement.
- Hors périmètre : accès aux environnements réels (onboarding irréversible), API citoyen réelle, exécution de la voie de
  secours elle-même (le process s'en charge), réception des réponses et accusés (#11, §150 lot 2), partitions.
- Critères de succès : (1) un message valide part en multipart avec sa clé d'idempotence et son mandat ; (2) un rejeu ne
  publie jamais deux fois (409, journal, preuve existante) ; (3) un citoyen sans consentement ne reçoit rien dans l'eBox et le
  process reçoit la voie de secours ; (4) chaque envoi laisse une ligne d'audit que la base refuse de modifier ou supprimer (sauf le rôle propriétaire, voir Q10) ;
  (5) aucun NISS en clair dans le journal ; (6) le jeton ne part que vers des hôtes autorisés.
- Contraintes : JPA seulement (§122), changeSet nouveau (054), un schéma par tenant, SSRF (`OutboundUrlGuard`, pas de
  redirection), secrets chiffrés au repos (instance de connecteur), specs ONSS non versionnées (licence).
- Validé par l'utilisateur le : 2026-11-20 (Benoit, via le coordinateur).

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Q1. Entreprise dotée d'une eBox **non exclusive** (`exclusivelyEbox: false`) : faut-il aussi la voie de secours ? | a) eBox seule ; b) eBox + copie papier/e-mail ; c) réglable par type de dossier | b par défaut (aucun risque juridique de non-réception), c au lot 2 si les communes le demandent | hypothèse H1 (b) → §155 |
| Q2. Une commune a-t-elle accès à **Registered Mail 1.1** ? La spec vise « social security public institutions » (scopes `ksz-bcss`). | a) Registered Mail 1.1 ; b) `registeredMailType: nonQualifiedRegisteredMail` de e-Box REST 3.8 ; c) les deux, réglable | Demander à eBoxIntegration@smals.be ; garder a (fait) et ajouter b au lot 2 si a est refusé | ouverte |
| Q3. Registered Mail 1.1 ne déclare pas l'en-tête `On-Behalf-Of-Enterprise-Number` : comment un prestataire publie-t-il un recommandé pour une commune ? | a) pas de mandat (le client OAuth est celui de la commune) ; b) en-tête quand même | a (fait : pas d'en-tête), à confirmer avec Smals | ouverte |
| Q4. `paymentData` existe aussi dans le format entreprise 3.8, alors que le cadrage le met dans l'extension citoyen. | a) garder citoyen seul ; b) remonter le paiement dans le modèle commun | b si une commune facture des entreprises (taxes) ; changement local au modèle | ouverte |
| Q5. Voie citoyen : la spec 3.8 déclare aussi un serveur `/REST/ebox/citizen/messageRegistry/v3` (Smals). Passer par Smals si le secteur citoyen est déclaré à l'onboarding, ou par BOSA ? | a) Smals (même adaptateur, X.509) ; b) BOSA (stub actuel) ; c) les deux | Demander à BOSA/ONSS ; le port permet c sans toucher au domaine | ouverte |
| Q6. Scope OAuth de l'appel Federation (`eboxPreferences/search`, `citizenEboxConsents/search`) : la spec 1.4 n'en déclare aucun. | scope de publication (fait) ou scope dédié | à confirmer en acceptation | hypothèse |
| Q7. Conservation du journal : 10 ans à partir du 1er janvier suivant (ATREQ01) ; la base interdit toute suppression. | a) jamais de purge en V1 ; b) purge par un rôle PostgreSQL dédié qui désactive le déclencheur | a en V1, b quand la première échéance approche (2037) | ouverte |
| Q8. Clé du HMAC du NISS dérivée de la clé maîtresse des connecteurs : une rotation de cette clé coupe la comparaison avec les lignes anciennes. | a) clé maîtresse (fait) ; b) clé dédiée `APP_EBOX_AUDIT_HASH_KEY` | a tant que la rotation n'est pas outillée ; la version `SSIN_HMAC_V1` permet de passer à b | hypothèse |
| Q9. Autorisation d'usage du NISS pour cibler un citoyen (même sujet que CSAM, archive #210) | — | prérequis juridique avant toute mise en production de la voie citoyen | ouverte (#13) |
| Q10. Le rôle applicatif possède `ebox_send_audit` et peut désactiver ses déclencheurs (revue M1). Séparer un rôle de migration ? | a) limite documentée (fait) ; b) rôle de migration propriétaire, rôle applicatif en `INSERT`/`SELECT` sur le journal ; c) b pour toutes les tables | b ou c avant la validation BOSA ; touche `provision-tenant.sh`, Liquibase (datasource dédiée) et le déploiement | ouverte (Benoit) |
| Q11. `allowedHostSuffixes` de l'instance `ebox-outbound` est réglé par l'administrateur du tenant (revue B3) : un admin pourrait envoyer le jeton ailleurs. | a) laisser (fait, comme §150) ; b) liste fixée par la plateforme ; c) intersection plateforme ∩ tenant | c au lot 2, avec l'écran du connecteur | ouverte |

## Conception

- Faits vérifiés dans les sources (specs locales, non versionnées) :
  - e-Box REST 3.8 `POST /ebox/messages` : multipart `messageToPublish` + `upfile1..20` (+ `qrfile`), en-têtes
    `Idempotency-Key` (409 sur doublon) et `On-Behalf-Of-Enterprise-Number`, réponse 201 `PublicationStatusMessage`
    (`messageId`) ; `Digest` = `{digestMethod: "sha_256", digestValue}` en base64url ; scopes `publicationsender`, etc.
  - Registered Mail 1.1 `POST /registeredMails` : multipart `registeredMailToPublish` + `mainContent` + `annex1..9`,
    `customerReference` obligatoire, réponse 200 `ProofOfDispatch` (`signature`) ; `GET /proofsOfDispatch?customerReference=` ;
    `Digest` = `{method: "sha256", value}` (forme différente de 3.8).
  - Federation 1.4 : `eboxPreferences/search` (« There is no consent to check for enterprise e-Box », `exclusivelyEbox`),
    `citizenEboxConsents/search` (`hasConsent`, citoyen absent = inconnu).
  - Code : `OAuth2TokenProvider` (`PRIVATE_KEY_JWT`, cache par empreinte de configuration, `invalidate` refusé dans les
    30 s qui suivent l'émission), `OutboundUrlGuard`, `PinnedHttpClient.exchange` (ne lisait que le corps d'un 200 : il lit
    désormais celui d'un 201 pour une méthode avec corps, revue B4 : `PinnedHttpClientTest.exchangeReadsACreatedBodyOnlyForAMethodWithABody`).
- Composants (lot 1) : domaine `be.myrmacta.domain.ebox` (modèle, `EboxDelivery`, ports `EboxChannel`,
  `EboxSendAuditTrail`, `SendEboxMessageUseCase`) ; `HttpEboxEnterpriseChannel` + `EboxOutboundSettings` (connecteurs
  intégrés, pour réutiliser `OAuth2TokenProvider`) ; `StubEboxCitizenChannel` ; persistance `infrastructure.persistence.ebox`
  (changeset 054) ; `EboxSendConfig`.
- Scénarios de test : `EboxOutgoingMessageTest`, `EboxDeliveryPolicyTest`, `SendEboxMessageUseCaseImplTest` (domaine) ;
  `HttpEboxEnterpriseChannelTest` (vraie chaîne HTTP contre `FakeEboxSenderServer` : jeton signé, multipart, 409, 400, 503,
  délai, 401 renouvelé une fois, recommandé et preuve, hôtes refusés, cas d'usage complet) ; `StubEboxCitizenChannelTest` ;
  `EboxSendAuditPersistenceIT` (PostgreSQL : ajout, rejeu, NISS jamais en clair, `UPDATE`/`DELETE`/`TRUNCATE` refusés, cas
  d'usage câblé).

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | Modèle commun + extension citoyen, port et cas d'usage d'envoi, voie entreprise contre faux serveur (publication, recommandé et preuve, préférence eBox), stub citoyen, consentement et voie de secours, journal d'audit en ajout seul | livré (branche `feat/ebox-envoi`, non poussée) | — |
| 2 | Type de connecteur `ebox-outbound` (schéma de configuration, validation, libellés FR/EN du catalogue §142, écran admin, guide) ; brique BPMN « Envoyer en eBox » (pièce PDF du dossier, langue du dossier, clé d'idempotence dossier+étape, variables `eboxIssue`, `eboxVoieSecours`, `eboxMessageId`, preuve de dépôt versée au dossier) ; exemple BPMN avec passerelle vers la voie de secours ; test traversant la chaîne process | à faire | — |
| 3 | Selon les réponses : `nonQualifiedRegisteredMail` (Q2), paiement entreprise (Q4), voie citoyen Smals (Q5), consultation du journal (export pour BOSA, rôle `TENANT_ADMIN`, ATREQ03) | à faire | — |
| 4 | Adaptateur citoyen BOSA réel (après NDA), recette en acceptation | bloqué (accès) | — |

Prochaine action : réponses de Benoit à Q1 à Q5, puis lot 2.

## Preuves

| Date | Commande | Résultat |
|---|---|---|
| 2026-11-20 | `mvnw test -Dtest='Ebox*Test,SendEbox*Test,HttpEbox*Test,StubEbox*Test,EboxSendAuditPersistenceIT,ArchitectureRulesTest,PinnedHttpClient*Test'` | 112 tests, 0 échec (après correctifs : en-tête `Content-Type` de la recherche de préférence, horloge du test 401) |
| 2026-11-20 | `./mvnw -Dmaven.compiler.release=21 -Dskip.frontend=true verify` | BUILD SUCCESS : 5 416 tests, 0 échec, 0 erreur, 5 ignorés (existants) |
| 2026-11-20 | `scripts/check-docs.sh` | vert |
| 2026-11-20 | mêmes tests ciblés, après les correctifs de revue (M2 à M5, B1, B2, B4, ordre JSON) | 126 tests, 0 échec |

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
| M1 rôle applicatif propriétaire du journal (déclencheurs contournables) | moyenne | limite et mesure cible au §155, Q10 |
| M2 recommandé sans `Idempotency-Key` (double envoi possible) | moyenne | réservation 055, erreur explicite sans renvoi |
| M3 voie eBox réutilisée au rejeu malgré un consentement retiré | moyenne | consentement constaté de nouveau |
| M4 rejeu d'un recommandé publié sans sa preuve | moyenne | preuve relue par `messageId` |
| M5 mémoire ≈ 4 × la taille des pièces | moyenne | une copie, empreinte unique, corps à taille exacte |
| B1 2xx inexploitable, `clientId` non borné | basse | issue incertaine journalisée ; borne 200 |
| B2 404 de la fédération lu comme « pas d'eBox » | basse | erreur |
| B3 suffixes d'hôtes réglés par l'admin du tenant | basse | Q11 |
| B4 201 lu par `exchange` sans test | basse | test unitaire |
| CI PIT (JDK 25) : ordre de `Map.of` dans le JSON publié | — | `LinkedHashMap`, test par nœuds JSON |
