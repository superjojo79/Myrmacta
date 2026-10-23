# Édition du schéma des champs dynamiques — écran admin `DossierType`

> **Statut : première analyse fonctionnelle, aucune décision technique tranchée.** À affiner en
> phase `dev-lifecycle` (requirements → design) avant tout code — voir « Points ouverts » en fin de
> document. Ne pas confondre avec la feature **livrée** `edition-champs-dossier` (PR #53) : celle-ci
> permet d'éditer les **valeurs** des champs sur un dossier existant (`PATCH
> /api/dossiers/{id}/fields`, écran détail dossier). Le présent document couvre l'écran symétrique
> côté **schéma** : permettre à un `TENANT_ADMIN` de définir la **liste des `FieldDefinition`** d'un
> `DossierType` depuis l'UI — aujourd'hui impossible, cf. constat ci-dessous.

---

## 1. Constat — l'écart actuel

[`GUIDE-UTILISATEUR.md` §1](../../GUIDE-UTILISATEUR.md#1-préalable--configurer-un-type-de-dossier)
documente déjà la limite en toutes lettres :

> **Limite actuelle de l'écran :** ce formulaire ne permet pas de définir les **champs
> dynamiques** du type (…) — le type est créé avec une liste de champs vide. (…) s'adresser à
> l'équipe technique si un type a besoin de champs métier spécifiques dès sa création.

Constat confirmé côté code :

- `frontend/src/views/admin/DossierTypesAdminView.vue` — le formulaire de création appelle
  `createDossierType({ …, fieldDefinitions: [] })` **en dur**. Aucun champ de saisie, aucune ligne
  de tableau, aucune action ne touche `fieldDefinitions` sur cet écran, à la création comme à la
  modification (`updateDossierType` n'y est jamais appelé qu'avec `active`, `retention*`,
  `slaTargetHours`).
- Le **backend est pourtant déjà prêt** — aucune évolution de domaine, de port ou de persistance
  n'est nécessaire pour cette feature :
  - `POST /api/dossier-types` et `PATCH /api/dossier-types/{code}`
    (`DossierTypeController`) acceptent déjà `fieldDefinitions: List<FieldDefinition>` en entrée et
    le renvoient dans `DossierTypeResponse` — voir `DossierApiDtos.java`.
  - Le domaine (`DossierType`, `FieldDefinition`, `CreateDossierTypeUseCase`,
    `UpdateDossierTypeUseCase`) porte la liste complète sans aucune restriction liée à son
    origine (API REST, batch, MCP).
  - Un serveur MCP interne (`DefineDossierTypeTool`, outil `define_dossier_type`) permet déjà de
    définir le schéma complet d'un type par un appelant agent/LLM, avec relecture-avant-écriture
    (`dryRun`) — la seule voie *actuellement* opérationnelle pour peupler `fieldDefinitions` sans
    passer par du SQL manuel ou un import direct.

Autrement dit : la donnée, la validation et l'API existent ; c'est un **écran manquant**, pas un
trou d'architecture.

---

## 2. Périmètre proposé

### Inclus

1. Sur l'écran **Admin → Types** (`/admin/dossier-types`, `TENANT_ADMIN`) : un éditeur de la liste
   `fieldDefinitions` d'un `DossierType`, en création **et** en modification.
2. Pour chaque `FieldDefinition` : `name`, `label`, `type` (`STRING`, `NUMBER`, `BOOLEAN`, `DATE`,
   `SELECT`, `FILE`), `required`, `defaultValue`, `options` (uniquement pour `SELECT`), `group`,
   `sensitive`.
3. Ajout / suppression / réordonnancement des champs dans le formulaire, avant sauvegarde.
4. Bascule du drapeau `allowUnknownFields` du type (déjà exposée par l'API, absente de l'écran
   aujourd'hui — cf. §4.4 du contrôleur, jamais renseignée par `DossierTypesAdminView.vue`).
5. Un avertissement de compatibilité avant sauvegarde d'une modification de schéma sur un type déjà
   utilisé par des dossiers existants (§4 et §5.3 ci-dessous) — c'est le rôle du
   `CheckDossierTypeSchemaCompatibilityUseCase`, déjà écrit côté domaine mais **jamais appelé par
   aucun chemin REST** à ce jour (seul l'outil MCP l'invoque).

### Explicitement hors périmètre (V1)

| Sujet | Pourquoi hors périmètre |
|---|---|
| Migration des valeurs existantes lors d'un changement de type de champ (`STRING` → `NUMBER`…) | Aucun mécanisme de migration n'existe dans le domaine ; le check de compatibilité *détecte* l'incompatibilité, il ne la résout pas. |
| Renommage d'un champ avec réaffectation transparente des valeurs déjà stockées | `FieldDefinition.name` est la clé technique dans `Dossier.fields` (`Map<String,Object>`) ; renommer revient aujourd'hui à créer un nouveau champ et abandonner l'ancien comme "inconnu" dans les dossiers existants (accepté seulement si `allowUnknownFields=true`). Pas de UX de "renommer avec migration" en V1. |
| Suppression d'un champ avec purge des valeurs déjà stockées | Idem — supprimer une `FieldDefinition` ne touche pas `Dossier.fields` des dossiers existants ; la valeur reste en base, simplement non affichée/validée tant que `allowUnknownFields=true`. |
| Édition des **valeurs** de champs sur un dossier | Déjà livré (`edition-champs-dossier`, PR #53) — écran détail dossier, `DossierFieldsEditor.vue`. |
| Versionnement de schéma / historique des évolutions de `fieldDefinitions` | Aucune table d'historique de schéma n'existe ; à instruire séparément si le besoin métier apparaît. |

---

## 3. Modèle domaine — rien à faire côté backend a priori

```java
// domain/dossier/model — déjà existant, aucune évolution requise
public record FieldDefinition(
    String       name,         // clé technique — sert de clé dans Dossier.fields
    String       label,        // libellé affiché dans les formulaires de saisie
    FieldType    type,         // STRING | NUMBER | BOOLEAN | DATE | SELECT | FILE
    boolean      required,
    String       defaultValue,
    List<String> options,      // pour SELECT uniquement
    String       group,        // regroupement visuel (DynamicFields.vue affiche "Général" si vide)
    Boolean      sensitive      // masquage en lecture (décision n° 23, TODO §23 — livré)
) {}

public record DossierType(
    UUID id, String tenantId, String code, String label, String workflowKey,
    boolean active,
    boolean allowUnknownFields,      // pas de champ de saisie sur l'écran aujourd'hui — à ajouter
    List<FieldDefinition> fieldDefinitions,
    Integer retentionDays, boolean retentionNever, Integer slaTargetHours,
    String createdBy, String updatedBy
) {}
```

Deux use cases déjà câblés à consommer tels quels :

```java
// domain/dossier/port/in — déjà existants
CreateDossierTypeUseCase.create(CreateDossierTypeCommand, AccessContext.User)
UpdateDossierTypeUseCase.update(UpdateDossierTypeCommand, AccessContext.User)
```

**Convention de remplacement intégral, pas de diff.** `UpdateDossierTypeCommand.fieldDefinitions()`
remplace la liste entière — il n'existe pas d'endpoint "ajouter un champ" / "modifier un champ"
unitaire. C'est déjà la convention établie côté MCP (`define_dossier_type` : *"the full field
schema — the complete list every call, not a diff"*) ; l'écran doit suivre la même règle plutôt que
d'introduire un second modèle de mise à jour (diff) pour la même ressource. Concrètement : l'écran
charge la liste actuelle en mémoire, l'utilisateur l'édite localement (ajout/suppression/edit de
lignes), et la sauvegarde envoie la liste complète reconstituée dans `PATCH
/api/dossier-types/{code}`.

---

## 4. Le point qui n'est pas prêt côté backend : le check de compatibilité n'est pas branché sur REST

C'est la découverte la plus significative de cette analyse, et elle **conditionne le design de
l'écran** :

```java
// domain/dossier/port/in — existe, câblé en bean Spring (DossierConfig), invoqué uniquement
// aujourd'hui par DefineDossierTypeTool (MCP)
public interface CheckDossierTypeSchemaCompatibilityUseCase {
    Result check(Query query); // Query(tenantId, dossierTypeCode, newFieldDefinitions, allowUnknownFields)
    record Result(int incompatibleCount, List<String> sampleReferences) {
        public boolean compatible() { return incompatibleCount == 0; }
    }
}
```

Ce use case relit tous les dossiers existants du type et rejoue `DossierFieldValidator` avec le
**nouveau** schéma proposé, pour détecter les dossiers dont les valeurs actuelles deviendraient
invalides (champ désormais `required` mais absent, valeur `SELECT` hors des nouvelles `options`,
type incompatible, champ devenu "inconnu" si `allowUnknownFields=false`…).

Le commentaire sur `DossierTypeSchemaIncompatibleException` est explicite sur la décision déjà
actée pour le MCP :

```java
/** D-SCHEMA-COMPAT=B — raised by callers (not the compatibility check itself) that choose to block. */
public class DossierTypeSchemaIncompatibleException extends IllegalStateException { … }
```

**`UpdateDossierTypeUseCaseImpl.update(...)` n'invoque ce check nulle part.** `PATCH
/api/dossier-types/{code}` accepte donc aujourd'hui silencieusement une modification de
`fieldDefinitions` qui rendrait des dossiers existants incohérents avec le schéma déclaré — sans
avertissement, sans blocage. Seul l'outil MCP `define_dossier_type` applique le check (et bloque
systématiquement en cas d'incompatibilité, sans option de passage en force). `DossierExceptionHandler`
(REST) ne mappe d'ailleurs pas `DossierTypeSchemaIncompatibleException` vers un statut HTTP — rien
n'y répond aujourd'hui côté web.

Ce nouvel écran ne peut donc pas se contenter d'appeler l'API existante telle quelle : il faut
décider comment il s'insère dans D-SCHEMA-COMPAT=B (voir Points ouverts, D-UI-SCHEMA-COMPAT).

---

## 5. Esquisse fonctionnelle de l'écran

### 5.1 Formulaire de création (type sans dossier existant)

Reprend le formulaire actuel de `DossierTypesAdminView.vue` (`code`, `label`, `workflowKey`,
rétention, SLA) et y ajoute :

- une case à cocher `allowUnknownFields` ;
- un éditeur de liste `fieldDefinitions`, vide par défaut, avec un bouton « Ajouter un champ » qui
  insère une ligne éditable (name / label / type / required / defaultValue / options si `SELECT` /
  group / sensitive) et un bouton de suppression par ligne.

Aucun dossier n'existe encore pour un type en cours de création → pas de check de compatibilité
nécessaire ici, seulement la validation de forme (voir §5.4).

### 5.2 Édition d'un type existant

Le tableau des types (déjà présent) gagne une action « Modifier les champs » (ou équivalent) qui
ouvre le même éditeur de liste, pré-rempli avec `type.fieldDefinitions` récupéré via `GET
/api/dossier-types/{code}` (déjà exposé, déjà utilisé par le store `dossier.ts` côté détail
dossier).

### 5.3 Avertissement de compatibilité avant sauvegarde

Sur **modification** uniquement (un type neuf n'a pas de dossiers) :

1. Au clic sur « Enregistrer », l'écran doit pouvoir savoir, *avant* d'appeler `PATCH
   /api/dossier-types/{code}`, si le nouveau schéma proposé casse des dossiers existants — pour
   afficher un avertissement plutôt que de découvrir le problème après coup.
2. Cela suppose un point d'entrée REST pour `CheckDossierTypeSchemaCompatibilityUseCase`,
   aujourd'hui inexistant (§4). Sans lui, l'écran ne peut informer l'admin qu'*après* sauvegarde, ou
   pas du tout — voir D-UI-SCHEMA-COMPAT dans les points ouverts, qui conditionne cette section.
3. Maquette d'interaction proposée (à valider) :

   ```
   ⚠ Cette modification du schéma rend 3 dossier(s) existant(s) incompatibles
     avec les nouvelles règles (ex. DOS-SINISTRE-2024-000012, DOS-SINISTRE-2024-000031, …).
     Les dossiers concernés ne seront pas modifiés automatiquement.

     [ Annuler ]   [ Enregistrer quand même ]
   ```

   — sous réserve que D-UI-SCHEMA-COMPAT retienne un mode "avertir" plutôt que "bloquer
   strictement" (le MCP bloque strictement aujourd'hui, sans option de passage en force ; l'écran
   admin pourrait vouloir un comportement différent, cf. points ouverts).

### 5.4 Règles de validation côté formulaire (avant tout appel réseau)

Aucune de ces règles n'est aujourd'hui imposée côté domaine (`DossierType`, `FieldDefinition`) — à
faire porter par le formulaire, a minima :

- `name` non vide, unique **au sein du même type** (aucune contrainte d'unicité de `name` n'existe
  aujourd'hui dans `DossierType`/`CreateDossierTypeUseCaseImpl` — deux champs avec le même `name`
  s'écraseraient silencieusement dans `Dossier.fields`, une `Map` indexée par ce nom) ;
- `label` non vide ;
- au moins une valeur dans `options` si `type = SELECT` (repris de la maquette d'affichage déjà en
  place dans `DynamicFields.vue`, qui n'affiche aucune option pour un `SELECT` vide) ;
- `defaultValue` cohérente avec `type` (ex. pas de texte libre pour un `NUMBER`/`DATE`/`BOOLEAN`) —
  même famille de règles que `DossierFieldValidator` côté serveur (`matchesType`), à répliquer côté
  formulaire pour un retour immédiat, la validation serveur restant systématiquement invoquée à la
  création/modification effective d'un **dossier** (pas à celle du type — cf. §4).

---

## 6. Rôle et navigation

Aucun changement de rôle : `POST`/`PATCH /api/dossier-types` exigent déjà
`@PreAuthorize("hasRole('TENANT_ADMIN')")` (`DossierTypeController`), cohérent avec l'entrée de menu
existante **Admin → Types** déjà restreinte à `TENANT_ADMIN`
([GUIDE-UTILISATEUR.md](../../GUIDE-UTILISATEUR.md#0-rôles-et-navigation)). Pas de nouvelle route, pas
de nouveau rôle.

---

## 7. Réutilisation front — écart de convention à trancher

Deux composants existent déjà côté **valeurs** de champs et sont directement réutilisables comme
référence (pas nécessairement comme code partagé, le contexte diffère : édition de *définitions* vs
édition de *valeurs*) :

- `frontend/src/components/dossier/DynamicFields.vue` — logique de regroupement par `group`
  (`def.group || 'Général'`), rendu par `type` (`BOOLEAN` → checkbox, `SELECT` → select, sinon
  `input[type=text|number|date]`).
- `frontend/src/dossier/diffDossierFields.ts` — calcul de patch minimal (D-DIFF), à ne **pas**
  réutiliser tel quel ici puisque §3 retient un remplacement intégral de `fieldDefinitions`, pas un
  diff.

Point de convention à noter : malgré [11-frontend.md](11-frontend.md) qui documente PrimeVue comme
librairie de composants retenue, les écrans admin existants (`DossierTypesAdminView.vue`,
`DynamicFields.vue`, `DossierFieldsEditor.vue`) sont tous en éléments HTML natifs stylés en scoped
CSS, avec des `data-testid` pour Playwright — pas de composants PrimeVue. Le nouvel éditeur de
`fieldDefinitions` devrait suivre le même pattern que l'écran qu'il étend
(`DossierTypesAdminView.vue`) plutôt que d'introduire PrimeVue isolément sur un seul sous-formulaire
— sujet à confirmer en phase design plutôt qu'à trancher silencieusement ici (cf. points ouverts).

---

## 8. Points ouverts

À trancher en phase requirements/design (`dev-lifecycle`), pas dans ce document :

1. **D-UI-SCHEMA-COMPAT** — comment l'écran s'articule avec D-SCHEMA-COMPAT=B (§4) :
   - (a) exposer un nouvel endpoint REST dédié au check (ex. `POST
     /api/dossier-types/{code}/schema-compatibility`, préflight sans persistance, miroir du
     `dryRun` déjà pratiqué côté MCP) puis appeler `PATCH` seulement après confirmation explicite de
     l'admin ;
   - (b) faire invoquer le check directement par `UpdateDossierTypeUseCaseImpl.update(...)` et
     bloquer par défaut (alignerait enfin le comportement REST sur celui du MCP, actuellement
     divergent) — implique de mapper `DossierTypeSchemaIncompatibleException` dans
     `DossierExceptionHandler` (409, sur le modèle de `DossierConflictException`) et de retirer
     l'incohérence actuelle plutôt que de la reproduire côté UI ;
   - (c) ne rien changer côté backend pour cette V1 et se limiter à un écran qui, lui, n'expose que
     ce que l'API permet déjà (pas d'avertissement de compatibilité en V1) — livre la feature plus
     vite mais laisse l'incohérence relevée en §4 non résolue.
   Nécessite un avis produit (bloquer vs avertir) et pas seulement technique — c'est un choix de
   compromis rigueur/flexibilité pour l'admin tenant, du même ordre que celui déjà tranché pour
   `allowUnknownFields`.
2. **Unicité de `name` dans `fieldDefinitions`** — à valider uniquement côté formulaire (§5.4), ou
   également ajoutée comme garde-fou domaine (`DossierType` ou `DossierFieldValidator`) pour
   couvrir aussi la création via MCP/API directe, qui n'est aujourd'hui protégée par rien ?
3. **`allowUnknownFields`** — absent de l'écran actuel malgré son exposition API complète ; à
   confirmer que son ajout fait bien partie du périmètre de cette feature (proposé en §2) plutôt que
   d'un lot séparé.
4. **Alignement maquette** — [11-frontend.md](11-frontend.md) impose de s'aligner sur
   `FlowForge - Maquettes UI.html` ; aucun écran de ce fichier ne couvre aujourd'hui l'édition du
   schéma de champs (recherche infructueuse lors de cette analyse). À vérifier en phase design avant
   d'improviser une disposition, ou à faire ajouter au mock si absent.
5. **`GUIDE-UTILISATEUR.md` §1** — à mettre à jour une fois la feature livrée (le paragraphe
   « Limite actuelle de l'écran » cité en §1 devient obsolète) : rappel de convention (§ « Workflow
   de feature » point 6 de la skill `flowforge-conventions`), non fait dans ce document puisqu'aucun
   code n'est encore livré.
6. **Nommage du lot** — aucun nom de feature n'est réservé dans `FILE-ATTENTE.md` /
   `DEFERRED-FEATURES.md` pour ce sujet à ce jour ; à choisir en séquençant avec le reste de la file
   (ne pas réutiliser `edition-champs-dossier`, déjà pris par la feature valeurs livrée).
