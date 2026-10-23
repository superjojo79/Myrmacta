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
generate/preview **et** à l'**activation**) : `T()`, `#beans`, `new`, `#request`, et tout
objet d'expression hors allowlist. Le POST draft reste 201 (le checker métier s'exécute à
l'activation, pas à la création). Le checker valide la même allowlist **sans** exécuter le
template (les variables métier absentes à l'activation ne font pas échouer la syntaxe — elles
font en revanche échouer le **rendu**, voir ci-dessous).

### Grammaire des expressions `${…}`

Référence : `SafeThymeleafExpressionSupport` (et ses tests `SafeThymeleafExpressionSupportTest`).
Le même évaluateur sert à l'aperçu (`POST /api/documents/preview`, outil MCP `preview_document`)
et à la génération (`documentGenerationTask`) ; le checker d'activation applique la même grammaire
à chaque `${…}` et `*{…}` du template, sans variables.

**Forme d'une expression** — exactement une des formes suivantes, rien après :

```
expression := littéral | chemin | "#vars." chemin | utilitaire
chemin     := ident ("." ident)*                   ident = lettre ou _, puis lettres, chiffres, _
utilitaire := "#" ("strings"|"dates"|"numbers"|"lists") "." méthode "(" [expression ("," expression)*] ")"
littéral   := 'texte' | "texte" | entier | décimal | true | false | null
```

- **Littéraux** : texte entre `'…'` ou `"…"` (échappement par `\'` ou en doublant le guillemet :
  `'it''s'`) ; entier éventuellement négatif (`42`, `-7` ; au-delà de l'`int`, un `long`) ;
  décimal `1.5` (pas de notation exponentielle) ; `true`, `false`, `null`.
- **Chemin** : le premier segment est une variable du modèle ; chaque segment suivant lit une clé
  de `Map`, un getter **public** sans argument `getX()`/`isX()` (hérité compris, hors `Object`) ou
  l'accesseur canonique d'un `record`. Jamais une méthode arbitraire, même publique, ni un membre
  non public. Les noms `class`, `getClass`, `wait`, `notify`, `notifyAll` (toute casse) sont
  refusés.
- **`#vars.<chemin>`** : équivalent au chemin direct ; `#vars` seul est refusé.
- **Utilitaires** : seules les méthodes **déclarées** sur `org.thymeleaf.expression.Strings`,
  `Dates`, `Numbers`, `Lists`, avec le bon nombre d'arguments (varargs admis). Les arguments sont
  eux-mêmes des expressions (imbrication permise :
  `#strings.toUpperCase(#strings.trim(nom))`) ; les nombres sont convertis vers le type du
  paramètre, un `Instant` vers `Date`. Rien ne peut suivre l'appel (`#strings.trim(x).length` est
  refusé).
- **`*{…}`** est évalué exactement comme `${…}`, contre les variables du modèle : `th:object`
  n'est **pas** pris comme racine (`<div th:object="${m}"><p th:text="*{x}">` cherche la variable
  `x`, pas `m.x`).

**Refusé dans les accolades** (`Unsupported Thymeleaf expression`, template refusé à
l'activation) : opérateurs de comparaison (`${statut == 'VALIDE'}`), `and`/`or`, ternaire
(`${a ? 'x' : 'y'}`), indexation (`${map['x']}`), appel de méthode sur une valeur
(`${map.get('x')}`, `${map.containsKey('x')}`), chaînage après un utilitaire, `T(…)`, `new`,
`#beans`, `#request`, tout autre objet `#…`. Sont aussi refusés à l'activation, quelle que soit la
graphie : `th:utext`, `th:inline`, l'inline non échappé `[(…)]` et les fragments (`th:replace`,
`th:insert`, `th:include`, `th:substituteby`) ; l'inline échappé `[[${…}]]` est accepté.

**Hors des accolades**, les opérateurs de l'expression standard Thymeleaf combinent les résultats
de plusieurs `${…}` déjà évalués : concaténation `${a} + ' ' + ${b}`, et aussi
`${statut} == 'VALIDE'`, `${a} and ${b}`, `${a} ? 'oui' : 'non'` (constaté avec
`preview_document`). L'opérateur `${x} ?: 'défaut'` ne sert à rien pour une valeur manquante :
`${x}` échoue avant que Thymeleaf n'applique le défaut.

**Condition** — idiome recommandé : `th:if="${#strings.equals(statut, 'VALIDE')}"`. Quand la
condition est fausse, l'élément et **tout son contenu** ne sont pas évalués : un `th:text` ou un
`th:each` qui y référence une variable absente ne fait pas échouer le rendu. La variable testée
par le `th:if` lui-même doit exister et être non nulle.

**Variable ou propriété absente ou nulle ⇒ échec du rendu.** Un premier segment absent ou nul
lève `TemplateProcessingException` « Unknown template variable: x » ; un segment suivant absent,
nul (clé de map présente avec la valeur `null` comprise) ou sans getter lève « Unknown template
property: … ». Il n'y a **aucune** résolution silencieuse en chaîne vide — seule une valeur
présente, y compris `""`, s'affiche. Cela vaut aussi pour un argument d'utilitaire :
`#strings.isEmpty(x)` ou `#strings.defaultString(x, '-')` échouent si `x` est absent ou nul. En
workflow, `documentGenerationTask` échoue sans produire de PDF.

**Valeurs facultatives : les calculer en amont.** Plutôt que de référencer directement un champ
qui peut manquer (`${dossierFields.complement}`), poser avant `documentGenerationTask` une brique
« Évaluer une expression » (`${businessExpressionTask}`, voir
[docs/guides/GUIDE-CONCEPTION-BPMN.md](../guides/GUIDE-CONCEPTION-BPMN.md) §3sexies) qui produit une variable avec
une valeur par défaut non nulle —
`$exists(dossier.fields.complement) ? dossier.fields.complement : ""` dans `flowable:formula`,
`complementAdresse` dans `flowable:formulaResult` — et référencer `${complementAdresse}` dans le
template. Toutes les variables du processus sont transmises au template, la variable ainsi
calculée comprise. Tester ensuite le template avec `preview_document`, avec et sans les champs
facultatifs.
