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
| `WORKFLOW_ADMIN` | Tenant entier | Déploiement de processus BPMN, réassignation de tâches, éditeur `/admin/bpmn-editor` |
| `DOSSIER_MANAGER` | Dossiers assignés / de son périmètre | Création, instruction, clôture de dossiers ; ajout d'éléments |
| `DOSSIER_VIEWER` | Dossiers de son périmètre | Lecture seule — dossier, historique, documents |
| `API_SERVICE` | Machine-to-machine | Appel des endpoints connecteurs (`/api/connectors/{id}/execute`) sans session utilisateur — flux `client_credentials` par tenant, voir [03-authentification.md](03-authentification.md#authentification-machine-to-machine--client_credentials) |

Cette liste est un point de départ — la granularité exacte (par exemple restreindre `DOSSIER_MANAGER`
à un sous-ensemble de `DossierType`) est un sujet à trancher dès que les premiers cas d'usage réels
sont connus.

### Où appliquer le contrôle

| Couche | Mécanisme | Exemple |
|---|---|---|
| REST — niveau méthode | `@PreAuthorize("hasRole('WORKFLOW_ADMIN')")` sur les contrôleurs | Réassignation de tâche, déploiement de processus |
| Use case — niveau type | `DossierAccessPolicy` + table `dossier_type_acl` (décision n° 20) | Accès à tout dossier d'un `DossierType` selon VIEW/EDIT |
| Visualisation workflow | Port dédié déjà identifié | `WorkflowParticipantPort` — complémentaire ; l'ACL de type gouverne l'accès à la fiche dossier |

### Décision n° 20 — contrôle d'accès au niveau type de dossier (A2 + B2 + C1)

**Règle :** le contrôle de rôle (`@PreAuthorize`) protège contre les actions hors périmètre de rôle ;
le contrôle ACL protège contre l'accès aux dossiers d'un `DossierType` hors périmètre de
l'utilisateur. L'ACL est définie **au niveau du type**, pas de l'instance de dossier : un grant
`VIEW` sur le type « Sinistre » donne accès à **tous** les dossiers de ce type.

> **Amendement.** La première formulation de B2 posait une table `dossier_acl` par instance.
> Elle est remplacée par `dossier_type_acl` : périmètre métier = famille de dossiers, pas
> partage dossier-par-dossier.

#### A2 — `AccessContext`

```java
// domain/dossier/model
public sealed interface AccessContext
    permits AccessContext.User, AccessContext.System, AccessContext.Service {

    record User(String userId, Set<String> roles) implements AccessContext {}
    record System(String reason) implements AccessContext {}    // "flowable", "outbox-relay"…
    record Service(String clientId) implements AccessContext {} // API_SERVICE
}
```

| Variante | Effet sur l'ACL |
|---|---|
| `User` | Évaluation de `DossierAccessPolicy` sur le `dossierTypeCode` |
| `System` | Contournement — moteur, relais, jobs |
| `Service` | **Refus** d'accès dossier — M2M limité aux endpoints connecteurs |

Les use cases dossier prennent le contexte en paramètre :
`GetDossierUseCase.get(UUID id, AccessContext ctx)`.
`DossierAwareTask` et `ConnectorTriggerRuntime` passent `AccessContext.system(...)`.

#### B2 — table `dossier_type_acl`

```java
public enum DossierPermission { VIEW, EDIT }  // EDIT implique VIEW

// domain/dossier/model
@Builder
public record DossierTypeAclEntry(
    UUID              id,
    String            tenantId,
    String            dossierTypeCode,   // clé du DossierType, pas l'id d'un dossier
    String            subjectUserId,
    DossierPermission permission,
    String            grantedBy,
    Instant           grantedAt
) {}
```

| Règle | Détail |
|---|---|
| Source de vérité | Pour `DOSSIER_MANAGER` / `DOSSIER_VIEWER` : grant sur le **type** ; s'applique à toutes les instances de ce type |
| Bypass rôle | `TENANT_ADMIN` et `WORKFLOW_ADMIN` contournent l'ACL (tous les types du tenant) |
| Administration | `GrantDossierTypeAccessUseCase` / `RevokeDossierTypeAccessUseCase` — réservés aux admins tenant (ou équivalent) |
| Création de dossier | Exige `EDIT` sur le `dossierTypeCode` cible (humain) ; pas de grant par instance à la création |
| Liste / recherche | Filtrée aux `dossierTypeCode` pour lesquels le sujet a au moins `VIEW` |
| Unicité | `(tenant_id, dossier_type_code, subject_user_id)` — une permission effective ; upgrade `VIEW`→`EDIT` possible |

À intégrer au modèle physique consolidé (point 25).

> **Création par trigger.** Les dossiers créés avec `createdBy = trigger:…` suivent la même
> règle : visibles / éditables dès qu'un humain a un grant sur le type — plus besoin de partage
> instance-par-instance ni de grant à l'assignation Flowable.

> **Lien avec le point 19.** Restreindre un rôle à un sous-ensemble de `DossierType` est
> matérialisé par cette ACL de type ; le point 19 ne garde que la liste des rôles et le mapping
> `realm_access.roles` → autorités Spring.

#### C1 — `DossierAccessPolicy` (service domaine)

Pur Java ; dépend de `DossierTypeAclRepository` (port sortant). Évalue toujours via le
`dossierTypeCode` (fourni directement, ou lu sur le `Dossier` chargé).

```java
// domain/dossier — service domaine, pas un port
public class DossierAccessPolicy {

    private final DossierTypeAclRepository acl;

    public void assertCanViewType(String dossierTypeCode, AccessContext.User user) {
        if (isTenantWideAdmin(user)) return;
        if (!acl.hasAtLeast(dossierTypeCode, user.userId(), DossierPermission.VIEW)) {
            throw new AccessDeniedException(dossierTypeCode, user.userId(), "VIEW");
        }
    }

    public void assertCanEditType(String dossierTypeCode, AccessContext.User user) {
        if (isTenantWideAdmin(user)) return;
        if (!acl.hasAtLeast(dossierTypeCode, user.userId(), DossierPermission.EDIT)) {
            throw new AccessDeniedException(dossierTypeCode, user.userId(), "EDIT");
        }
    }

    /** Raccourcis instance — délèguent au type du dossier. */
    public void assertCanView(Dossier dossier, AccessContext.User user) {
        assertCanViewType(dossier.dossierTypeCode(), user);
    }

    public void assertCanEdit(Dossier dossier, AccessContext.User user) {
        assertCanEditType(dossier.dossierTypeCode(), user);
    }

    private static boolean isTenantWideAdmin(AccessContext.User user) {
        return user.roles().contains("TENANT_ADMIN")
            || user.roles().contains("WORKFLOW_ADMIN");
    }
}

// domain/dossier/port/out
public interface DossierTypeAclRepository {
    boolean hasAtLeast(String dossierTypeCode, String userId, DossierPermission required);
    void grant(DossierTypeAclEntry entry);
    void revoke(String dossierTypeCode, String userId);
    List<DossierTypeAclEntry> listByType(String dossierTypeCode);
    List<String> typeCodesReadableBy(String userId); // pour filtrer list/search
}
```

Refus → `AccessDeniedException` → HTTP 403 (format d'erreur normalisé : point 24).

### Où ce contrôle s'applique — tous les chemins de lecture d'un dossier

**Mise à jour post-audit sécurité.** `GET /api/workflow-links/{linkId}/dossier` (panneau dossier de
la visualisation workflow, [10-visualisation-workflow.md](10-visualisation-workflow.md)) chargeait
initialement le dossier via `DossierRepository` après authz « participant workflow ou
TENANT_ADMIN/WORKFLOW_ADMIN » **uniquement** — sans repasser par `DossierAccessPolicy` (décision
DD-DOSSIER-LOAD, motivée par l'évitement d'une double vérification d'ACL). Un simple participant ou
observer du workflow, sans entrée `dossier_type_acl` de niveau VIEW, pouvait donc lire l'intégralité
des champs du dossier — incohérent avec le modèle d'accès décrit ci-dessus.

**Décision retenue (décision n° 34, [registre des décisions](../decisions/034-acl-type-de-dossier-sur-le-panneau.md)) :**
`WorkflowLinkVisualizationUseCaseImpl.getDossier` appelle désormais
`DossierAccessPolicy.assertCanView(dossier, actor)` **en plus** du contrôle « participant
workflow ». Un observer/assigné sans ACL VIEW voit toujours le diagramme, l'historique et les
tâches (ces endpoints ne renvoient aucune donnée métier du dossier), mais reçoit 403 sur le
panneau dossier tant qu'il n'a pas d'entrée ACL explicite.

### Masquage des champs sensibles — appliqué côté serveur

`FieldDefinition.sensitive` (décision n° 20, ci-dessus) n'était vérifié que côté frontend
(`SensitiveValue.vue`) — la valeur en clair était systématiquement présente dans la réponse JSON,
y compris pour un simple détenteur d'ACL `EDIT`. `DossierFieldRedactor` (domaine pur,
`com.flowforge.domain.dossier`) retire désormais du `Dossier.fields()` renvoyé toute clé marquée
`sensitive`, sauf pour `TENANT_ADMIN` — appliqué sur `GetDossierUseCaseImpl` et
`WorkflowLinkVisualizationUseCaseImpl.getDossier`. Les contextes `System` (génération de documents,
triggers connecteurs) ne sont pas concernés : ils ne rendent jamais la donnée à un navigateur.
Voir point 23 dans le [registre des décisions](../decisions/README.md) pour le contexte complet.

## Conformité des données (RGPD et assimilés)

Le produit traite des données potentiellement sensibles pour le compte de tiers (sinistres,
subventions, réclamations — voir README). Les points suivants doivent être tranchés avant la mise en
production, au même titre que les points sécurité déjà listés dans le [registre des décisions](../decisions/README.md).

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

- Les champs d'audit déjà présents constituent une base de traçabilité des écritures, mais ne
  couvrent pas les accès en lecture — à évaluer selon le niveau requis par les clients cibles
  (secteur assurance, secteur public) :
  - `Dossier` : `createdBy` / `updatedBy` / `closedBy` (+ horodatages associés) — décision n° 18 ;
  - `DossierElement` : `addedBy` / `addedAt` (un élément est ajouté, pas mis à jour — pas de
    `createdBy`/`updatedBy` sur cet agrégat).
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

Ces décisions sont désormais suivies dans le [registre des décisions](../decisions/README.md), avec leur contexte détaillé dans
[docs/analyses/REVUE-PRE-IMPLEMENTATION.md](../analyses/REVUE-PRE-IMPLEMENTATION.md).

| Sujet | Décision à prendre | Suivi |
|---|---|---|
| Granularité des rôles | Valider la liste des rôles de référence ci-dessus avec les premiers cas d'usage réels | Point 19 |
| Contrôle au niveau type | **Tranché** — A2 `AccessContext` + B2 `dossier_type_acl` + C1 `DossierAccessPolicy` | Décision n° 20 |
| Rétention | Définir le modèle de rétention par `DossierType` (durée, action à expiration) | Point 21 |
| Suppression cohérente base + stockage objet | Définir le mécanisme garantissant la purge conjointe PostgreSQL / MinIO | Point 22 |
| Classification des champs dynamiques | Décider si `FieldDefinition.sensitive` est nécessaire dès la V1 | Point 23 |
