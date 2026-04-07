# Templates de documents

> Stockage en base de données avec versionning et résolution multi-variants.  
> Intégration directe avec le moteur Flowable via les variables d'exécution.

---

### Stratégie retenue : base de données (PostgreSQL)

Les templates HTML Thymeleaf sont stockés en base — cohérent avec le contenu (TEXT), et permet le versionning natif avec contrôle de statut.

Plusieurs templates peuvent coexister pour un même type de document selon la situation dans le workflow ou les paramètres d'exécution : variant métier (B2B/B2C, standard/premium, niveau d'escalade…), langue du destinataire, étape du processus. La résolution applique une stratégie de fallback du plus spécifique au plus générique.

### Modèle de données

La clé de résolution est un triplet `(templateKey, variantKey, locale)`. `variantKey` et `locale` sont optionnels — `null` signifie "défaut".

```java
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
    columnNames = {"tenant_id", "template_key", "variant_key", "locale"}
))
public class DocumentTemplate {

    @Id @GeneratedValue
    private UUID id;

    private String templateKey;   // type de document  : "contrat", "facture", "relance"
    private String variantKey;    // variante métier    : "standard", "premium", "B2B"
                                  //                      null = défaut
    private String locale;        // langue             : "fr", "nl", "en"
                                  //                      null = défaut

    private String label;
    private int    version;       // 1, 2, 3…

    @Enumerated(EnumType.STRING)
    private TemplateStatus status; // DRAFT, ACTIVE, ARCHIVED

    @Column(columnDefinition = "TEXT")
    private String htmlContent;   // contenu Thymeleaf complet

    private String  tenantId;
    private Instant createdAt;
    private Instant updatedAt;
    private String  updatedBy;
}
```

### Cycle de vie des versions

```
DRAFT → ACTIVE → ARCHIVED
```

- Une seule version `ACTIVE` par `(tenantId, templateKey, variantKey, locale)` à tout instant
- La résolution à la génération cible toujours la version `ACTIVE`
- Le passage en `ACTIVE` archive automatiquement la version précédente

Contrainte PostgreSQL — unicité de la version active par combinaison :

```sql
CREATE UNIQUE INDEX uq_template_active
    ON document_template (
        tenant_id,
        template_key,
        COALESCE(variant_key, ''),
        COALESCE(locale, '')
    )
    WHERE status = 'ACTIVE';
```

### Résolution en cascade

Le resolver applique un fallback du plus spécifique au plus générique :

```
1. (templateKey, variantKey, locale)  — correspondance exacte
2. (templateKey, variantKey, null)    — variant sans contrainte de langue
3. (templateKey, null,       locale)  — template défaut dans la bonne langue
4. (templateKey, null,       null)    — template défaut absolu
```

```java
@Value @Builder
public class TemplateResolutionRequest {
    String templateKey;  // obligatoire
    String variantKey;   // optionnel — issu des variables du processus Flowable
    String locale;       // optionnel — issu du profil du destinataire
}

@Service
@RequiredArgsConstructor
public class TemplateResolver {

    private final DocumentTemplateRepository repository;
    private final TenantContext tenantContext;

    public DocumentTemplate resolve(TemplateResolutionRequest req) {
        String tenant = tenantContext.getTenantId();

        return repository
            .findActive(tenant, req.getTemplateKey(),
                        req.getVariantKey(), req.getLocale())
            .or(() -> repository.findActive(tenant,
                        req.getTemplateKey(), req.getVariantKey(), null))
            .or(() -> repository.findActive(tenant,
                        req.getTemplateKey(), null, req.getLocale()))
            .or(() -> repository.findActive(tenant,
                        req.getTemplateKey(), null, null))
            .orElseThrow(() -> new TemplateNotFoundException(req));
    }
}
```

### Intégration Flowable — résolution depuis les variables d'exécution

Le `ServiceTask` de génération construit la requête de résolution à partir des variables du processus en cours :

```java
@Component("documentGenerationTask")
@RequiredArgsConstructor
public class DocumentGenerationTask implements JavaDelegate {

    private final TemplateResolver         resolver;
    private final DocumentGenerationService generator;
    private final DocumentStorageService    storage;

    @Override
    public void execute(DelegateExecution exec) {

        TemplateResolutionRequest req = TemplateResolutionRequest.builder()
            .templateKey((String) exec.getVariable("documentType"))
            .variantKey ((String) exec.getVariable("contractTier"))   // ex: "premium"
            .locale     ((String) exec.getVariable("recipientLocale"))// ex: "nl"
            .build();

        DocumentTemplate template = resolver.resolve(req);
        byte[] pdf = generator.generate(template, exec.getVariables());

        exec.setVariable("generatedDocumentId", storage.store(pdf));
    }
}
```

---

---

## Port domaine

```java
// domain/document/port/out
public interface DocumentTemplateRepository {
    Optional<DocumentTemplate> findActive(String tenantId, String templateKey,
                                           String variantKey, String locale);
    void save(DocumentTemplate template);
    List<DocumentTemplate> findAllByTenantAndKey(String tenantId, String key);
}
```
