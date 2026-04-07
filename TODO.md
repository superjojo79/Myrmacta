# Points à trancher — FlowForge

> Décisions architecturales et de sécurité identifiées lors de la revue de conception.
> Chaque point doit être résolu avant ou pendant le démarrage du développement.

---

## Sécurité

### [ ] 1. Validation du `tenantId` avant injection dans le `search_path`

**Problème :** `SchemaMultiTenantConnectionProvider` concatène directement le `tenantId` dans la requête SQL `SET search_path TO {tenantId}, public` sans sanitisation. Un `tenantId` malformé ou malveillant peut constituer une injection SQL.

**Décision à prendre :** Définir et documenter la règle de validation du `tenantId` (regex, longueur max, caractères autorisés) et choisir où elle s'applique — à la création du tenant uniquement, ou à chaque résolution de requête.

---

### [ ] 2. Comportement si le claim `tenant_id` est absent du token OIDC

**Problème :** Le `TenantResolutionFilter` ne rejette pas la requête si le claim `tenant_id` est absent. Le `TenantIdentifierResolver` tombe alors sur le fallback `"public"`, rendant le schéma public accessible.

**Décision à prendre :** Rejeter la requête avec une 401/403 si le claim est absent, ou réserver le schéma `public` à un usage strictement technique inaccessible via l'API.

---

### [ ] 3. Authentification des webhooks entrants

**Problème :** Le connecteur `http-rest` en mode TRIGGER reçoit des webhooks externes sans mécanisme d'authentification documenté. N'importe qui connaissant l'URL peut déclencher des créations de dossiers.

**Décision à prendre :** Choisir un mécanisme de vérification des webhooks entrants — signature HMAC (à la GitHub/Stripe), token secret par instance de connecteur, ou IP allowlist — et définir son emplacement dans l'architecture (filtre HTTP, `TriggerConnector`, ou couche dédiée).

---

### [ ] 4. Gestion des clés de chiffrement des secrets de connecteurs

**Problème :** Les champs `PASSWORD` des `ConnectorInstance` sont chiffrés AES-GCM avec une "clé par tenant", mais la gestion de ces clés n'est pas documentée : stockage, rotation, accès.

**Décision à prendre :** Définir où sont stockées les clés (colonne en base, variable d'environnement, vault externe type HashiCorp Vault ou Azure Key Vault) et documenter la stratégie de rotation.

---

## Architecture

### [ ] 5. `ConnectorInstance` — entité JPA ou objet domaine ?

**Problème :** L'entité JPA `ConnectorInstance` (annotée `@Entity`, `@Convert`) est passée directement aux interfaces `TriggerConnector` et `ServiceConnector`, qui sont des ports du domaine. Cela fait dépendre le domaine d'une classe d'infrastructure.

**Décision à prendre :** Introduire un objet domaine `ConnectorInstanceData` distinct de l'entité JPA, l'adaptateur assurant la conversion — ou accepter le compromis et le documenter explicitement.

---

### [ ] 6. Extensibilité de `WorkflowEventPublisher`

**Problème :** L'interface déclare une méthode par type d'événement (`publish(WorkflowStartedEvent)`, `publish(WorkflowCompletedEvent)`). Chaque nouvel état du workflow impose une modification de l'interface et de tous ses adaptateurs.

**Décision à prendre :** Adopter une signature générique `void publish(WorkflowEvent event)` avec hiérarchie de types, ou conserver les méthodes typées en acceptant l'évolution de l'interface à chaque nouvel événement.

---

### [ ] 7. Durabilité des événements de déclenchement workflow

**Problème :** Le bus `ApplicationEventPublisher` est synchrone et en mémoire. Un crash entre la réception d'un trigger (webhook, Kafka…) et le démarrage effectif de l'instance Flowable entraîne une perte de l'événement sans possibilité de rejeu.

**Décision à prendre :** Évaluer si le niveau de criticité du projet justifie un mécanisme de durabilité (outbox pattern avec table de staging, ou délégation à un broker externe), ou documenter explicitement que la perte d'événement en cas de crash est acceptable.

---

### [ ] 8. Validation des champs dynamiques des dossiers

**Problème :** `Dossier.fields` est une `Map<String, Object>` dont les valeurs ne sont pas validées contre le schéma `FieldDefinition` du `DossierType` associé au moment de la création ou de la mise à jour.

**Décision à prendre :** Définir où s'effectue la validation (use case `CreateDossierUseCase`, couche REST, ou les deux) et comment sont traités les champs inconnus ou les valeurs hors contrainte.

---

### [ ] 9. Duplication `usecase/` et `port/in/` dans le domaine dossier

**Problème :** La structure de packages de `04-dossiers.md` liste les mêmes noms dans `domain/dossier/usecase/` et dans `domain/dossier/port/in/`. Ce n'est pas cohérent avec le pattern documenté dans `01-architecture-hexagonale.md`.

**Décision à prendre :** Clarifier la convention : les interfaces sont dans `port/in/`, les implémentations dans `usecase/` — et l'appliquer uniformément à tous les modules du domaine.

---

## Opérationnel

### [ ] 10. Stratégie de migration Liquibase multi-tenant

**Problème :** Le choix de Liquibase est acté mais la stratégie opérationnelle n'est pas documentée, notamment : création du schéma à l'onboarding d'un nouveau tenant, migration de l'ensemble des tenants existants lors d'un déploiement, et gestion des erreurs partielles.

**Décision à prendre :** Documenter le bootstrap Liquibase multi-tenant (contexts `tenant-init` vs `migration`), la stratégie de lock lors de déploiements multi-instances, et le comportement attendu si la migration échoue sur un sous-ensemble de tenants.

---

### [ ] 11. Scaling horizontal et scheduler Flowable Community

**Problème :** Flowable Community gère les jobs asynchrones et le scheduler via une table de lock en base. En déploiement multi-instances (Kubernetes), sans coordination, le risque de double exécution de jobs existe.

**Décision à prendre :** Valider que le mécanisme de lock Flowable natif est suffisant pour le niveau de charge cible, ou définir une contrainte de déploiement (une seule instance en charge des jobs Flowable, les autres en mode API uniquement).

---

### [ ] 12. Fichier `architecture-technique v7.md` — référence ou archive ?

**Problème :** Le fichier `architecture-technique v7.md` coexiste avec les 11 fichiers thématiques numérotés qui en sont le découpage. Les deux sont trackés dans git, sans qu'il soit clair lequel est la référence à maintenir.

**Décision à prendre :** Supprimer `architecture-technique v7.md` et faire des fichiers numérotés la seule source de vérité, ou le conserver explicitement comme document de synthèse avec une note indiquant qu'il n'est plus mis à jour.
