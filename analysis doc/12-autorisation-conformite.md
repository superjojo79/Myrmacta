# Autorisation (RBAC) & conformité des données

> Complète [03-authentification.md](03-authentification.md) (qui répond à « qui est l'utilisateur ? »)
> en répondant à « qu'a-t-il le droit de faire ? » et « comment les données sont-elles protégées
> dans la durée ? ». Sujet identifié comme angle mort lors de la revue de conception.

---

## Modèle d'autorisation (RBAC)

### Principe

L'authentification (voir [03-authentification.md](03-authentification.md)) établit l'identité et le
`tenant_id`. L'autorisation est une couche distincte, appliquée **après** résolution du tenant, qui
détermine les actions permises à l'utilisateur authentifié à l'intérieur de son tenant.

Les rôles sont portés par le claim `realm_access.roles` du token OIDC (voir exemple dans
[02-multi-tenant.md](02-multi-tenant.md)) et mappés vers des autorités Spring Security.

### Rôles de référence

| Rôle | Portée | Capacités typiques |
|---|---|---|
| `TENANT_ADMIN` | Tenant entier | Configuration des `DossierType`, `ConnectorInstance`, templates, utilisateurs du tenant |
| `WORKFLOW_ADMIN` | Tenant entier | Déploiement de processus BPMN, réassignation de tâches, accès au cockpit Flowable |
| `DOSSIER_MANAGER` | Dossiers assignés / de son périmètre | Création, instruction, clôture de dossiers ; ajout d'éléments |
| `DOSSIER_VIEWER` | Dossiers de son périmètre | Lecture seule — dossier, historique, documents |
| `API_SERVICE` | Machine-to-machine | Appel des endpoints connecteurs (`/api/connectors/{id}/execute`) sans session utilisateur |

Cette liste est un point de départ — la granularité exacte (par exemple restreindre `DOSSIER_MANAGER`
à un sous-ensemble de `DossierType`) est un sujet à trancher dès que les premiers cas d'usage réels
sont connus.

### Où appliquer le contrôle

| Couche | Mécanisme | Exemple |
|---|---|---|
| REST — niveau méthode | `@PreAuthorize("hasRole('WORKFLOW_ADMIN')")` sur les contrôleurs | Réassignation de tâche, déploiement de processus |
| Use case — niveau ressource | Vérification explicite dans le use case (le rôle seul ne suffit pas — il faut aussi vérifier que la ressource appartient au périmètre de l'utilisateur) | `GetDossierUseCase` vérifie que l'utilisateur est `createdBy`, assigné, ou `TENANT_ADMIN` avant de retourner le dossier |
| Visualisation workflow | Port dédié déjà identifié | `WorkflowParticipantPort` (voir [10-visualisation-workflow.md](10-visualisation-workflow.md)) restreint l'accès aux assignés, initiateurs et observateurs configurés |

```java
// domain/dossier/port/out — nouveau port pour l'autorisation au niveau ressource
public interface DossierAccessPort {
    boolean canView(UUID dossierId, String userId, String tenantId);
    boolean canEdit(UUID dossierId, String userId, String tenantId);
}
```

**Règle :** le contrôle de rôle (`@PreAuthorize`) protège contre les actions hors périmètre de rôle ;
le contrôle de ressource (`DossierAccessPort`) protège contre l'accès à une ressource d'un même
tenant mais hors périmètre de l'utilisateur (ex. un `DOSSIER_MANAGER` ne doit pas nécessairement voir
tous les dossiers du tenant). Les deux sont nécessaires — l'un ne remplace pas l'autre.

---

## Conformité des données (RGPD et assimilés)

Le produit traite des données potentiellement sensibles pour le compte de tiers (sinistres,
subventions, réclamations — voir README). Les points suivants doivent être tranchés avant la mise en
production, au même titre que les points sécurité déjà listés dans [TODO.md](../TODO.md).

### Rétention et suppression

- **Politique de rétention par `DossierType`** — durée de conservation configurable par tenant et par
  type de dossier (ex. délai légal de conservation d'un dossier sinistre), avec archivage puis purge
  automatique après expiration.
- **Droit à l'effacement** — un `DossierElement` de type `ATTACHMENT` peut contenir des données
  personnelles (pièces jointes utilisateur). La suppression doit couvrir à la fois la ligne en base
  (schéma du tenant) et l'objet correspondant dans MinIO — les deux stores doivent être purgés de
  façon cohérente (transaction applicative ou job de réconciliation).
- **Suppression d'un tenant** — la stratégie d'isolation par schéma PostgreSQL (voir
  [02-multi-tenant.md](02-multi-tenant.md)) facilite la suppression complète d'un tenant
  (`DROP SCHEMA`) mais impose de documenter la purge symétrique du bucket MinIO associé.

### Traçabilité et minimisation

- Les champs `createdBy` / `updatedBy` / `closedBy` déjà présents sur `Dossier` et
  `DossierElement` (voir [04-dossiers.md](04-dossiers.md)) constituent une base d'audit, mais ne
  couvrent pas les accès en lecture — à évaluer selon le niveau de traçabilité requis par les clients
  cibles (secteur assurance, secteur public).
- Les `fields` dynamiques (`Map<String, Object>` en JSONB) n'ont pas de mécanisme de classification —
  rien n'empêche un tenant de déclarer un `FieldDefinition` contenant une donnée sensible (ex. numéro
  de sécurité sociale) sans le signaler. Un flag optionnel `sensitive: boolean` sur `FieldDefinition`
  permettrait au minimum de le documenter et, à terme, de conditionner le masquage à l'affichage ou
  l'exclusion des exports.

### Sous-traitance et localisation

- Le choix d'un hébergement mutualisé multi-tenant (voir README — modèle de déploiement) implique de
  documenter, pour un client donné, la localisation des données (région du cluster PostgreSQL /
  MinIO) si une exigence contractuelle ou réglementaire l'impose.
- Les webhooks sortants (voir [09-notifications.md](09-notifications.md)) transmettent des données du
  dossier vers des plateformes tierces (Make, n8n, Zapier) — le consentement / la base légale de ce
  transfert est une configuration tenant, pas un choix technique, et doit être matérialisée quelque
  part dans l'UI d'administration (hors périmètre de ce document, mais à ne pas oublier côté produit).

---

## Synthèse des décisions à trancher

| # | Sujet | Décision à prendre |
|---|---|---|
| 1 | Granularité des rôles | Valider la liste des rôles de référence ci-dessus avec les premiers cas d'usage réels |
| 2 | Contrôle au niveau ressource | Introduire `DossierAccessPort` (ou équivalent) avant l'implémentation de `GetDossierUseCase` |
| 3 | Rétention | Définir le modèle de rétention par `DossierType` (durée, action à expiration) |
| 4 | Suppression cohérente base + stockage objet | Définir le mécanisme garantissant la purge conjointe PostgreSQL / MinIO |
| 5 | Classification des champs dynamiques | Décider si `FieldDefinition.sensitive` est nécessaire dès la V1 |

> Ces points complètent la liste de [TODO.md](../TODO.md) — à terme, ils devraient y être fusionnés
> (section "Conformité") plutôt que maintenus dans un document séparé.
