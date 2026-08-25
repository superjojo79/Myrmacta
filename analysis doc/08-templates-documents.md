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
// domain/document/model — pur Java, aucune annotation JPA (décision n° 17,
// voir 01-architecture-hexagonale.md) — record, accesseurs sans préfixe (.id(), .templateKey()…)
@Builder
public record DocumentTemplate(
    UUID   id,
    String templateKey,   // type de document  : "contrat", "facture", "relance"
    String variantKey,    // variante métier    : "standard", "premium", "B2B" — nullable, null = défaut
    String locale,        // langue             : "fr", "nl", "en" — nullable, null = défaut

    String label,
    int    version,       // 1, 2, 3…

    TemplateStatus status, // DRAFT, ACTIVE, ARCHIVED

    String htmlContent,   // contenu Thymeleaf complet

    String  tenantId,
    Instant createdAt,
    Instant updatedAt,
    String  updatedBy
) {}
```

```java
// infrastructure/persistence/document — entité de persistance, porte la contrainte
// d'unicité (préoccupation de stockage, pas de domaine)
@Entity
@Table(name = "document_template", uniqueConstraints = @UniqueConstraint(
    columnNames = {"tenant_id", "template_key", "variant_key", "locale"}
))
public class DocumentTemplateJpaEntity {

    @Id @GeneratedValue
    private UUID id;

    private String templateKey;
    private String variantKey;
    private String locale;
    private String label;
    private int    version;

    @Enumerated(EnumType.STRING)
    private TemplateStatus status;

    @Column(columnDefinition = "TEXT")
    private String htmlContent;

    private String  tenantId;
    private Instant createdAt;
    private Instant updatedAt;
    private String  updatedBy;

    // conversion assurée par DocumentTemplateJpaAdapter — même principe que
    // DossierJpaAdapter, voir 04-dossiers.md
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
    private final TenantProperties tenantProperties; // décision n° 13 — constante de configuration

    public DocumentTemplate resolve(TemplateResolutionRequest req) {
        String tenant = tenantProperties.id();

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
    private final GenerateDocumentUseCase  generateDocument; // domain/document/port/in
    private final DocumentStoragePort      storage;          // domain/document/port/out

    @Override
    public void execute(DelegateExecution exec) {

        TemplateResolutionRequest req = TemplateResolutionRequest.builder()
            .templateKey((String) exec.getVariable("documentType"))
            .variantKey ((String) exec.getVariable("contractTier"))   // ex: "premium"
            .locale     ((String) exec.getVariable("recipientLocale"))// ex: "nl"
            .build();

        DocumentTemplate template = resolver.resolve(req);
        byte[] pdf = generateDocument.generate(template, exec.getVariables());

        exec.setVariable("generatedDocumentId", storage.store(pdf));
    }
}
```

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

> La génération PDF et le stockage passent par `GenerateDocumentUseCase` /
> `DocumentStoragePort` (voir [07-generation-documents.md](07-generation-documents.md),
> décision n° 18) — pas par des « Services » hors hexagone.

### Rendu Thymeleaf — allowlist B2 (D-40)

Les templates stockés sont rendus par `TemplateEngine` + `StandardDialect` **sans** SpEL Spring
(pas l'option A « moteur sans aucune expression utilitaire »). L'évaluateur custom autorise :

- les chemins de propriétés du modèle (`${title}`, `${dossier.reference}`) ;
- `#vars`, `#dates`, `#numbers`, `#strings`, `#lists` — uniquement les méthodes **déclarées**
  sur les objets Thymeleaf correspondants (pas `Object.getClass` / `wait` / `notify`).

Sont refusés (`TemplateProcessingException` / `InvalidTemplateSyntaxException` → **400** à
generate/preview **et** à l'enregistrement) : `T()`, `#beans`, `new`, `#request`, et tout
objet d'expression hors allowlist. Le checker valide la même allowlist **sans** exécuter le
template (les variables métier absentes à l'enregistrement ne font pas échouer la syntaxe).
