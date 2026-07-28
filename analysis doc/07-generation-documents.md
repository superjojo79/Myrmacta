# Génération de documents PDF

> Stack Thymeleaf → HTML → Flying Saucer → OpenPDF.  
> Les templates sont gérés dans [08-templates-documents.md](08-templates-documents.md).

---

## Stack de rendu

### Stack retenue

```
Thymeleaf  →  HTML  →  jsoup (XHTML)  →  Flying Saucer  →  OpenPDF
```

| Librairie | Rôle | Licence |
|---|---|---|
| Thymeleaf | Fusion template + données | Apache 2.0 |
| jsoup | HTML5 → XHTML strict | MIT |
| Flying Saucer (`xhtmlrenderer`) | Rendu XHTML → PDF layout | LGPL |
| OpenPDF | Écriture PDF (`flying-saucer-pdf-openpdf`) | LGPL / MPL |

### Dépendances Maven

```xml
<dependency>
    <groupId>org.xhtmlrenderer</groupId>
    <artifactId>flying-saucer-pdf-openpdf</artifactId>
    <version>9.9.4</version>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

### Alternatives écartées

- **iText 7 / pdfhtml** — licence AGPL incompatible avec un modèle SaaS/commercial fermé sans licence payante
- **Playwright headless** — écart si CSS3 devient bloquant (Flexbox, Grid) ; gardé en option de repli

### Playwright — option de repli

Conserver l'interface `PdfRenderer` pour switcher sans impact :

```java
public interface PdfRenderer {
    byte[] render(String html);
}
```

Activation via `application.yml` :

```yaml
pdf:
  engine: flying-saucer   # ou : playwright
```

---

## CSS print — contraintes Flying Saucer

Flying Saucer implémente **CSS 2.1** avec quelques extensions CSS 3 sélectives.

### Supporté

| Propriété / Feature | Support |
|---|---|
| `@page`, `size`, `margin` | ✅ |
| `page-break-before/after/inside` | ✅ |
| `display: table / table-cell` | ✅ |
| `position: fixed` (répété chaque page) | ✅ |
| `counter(page)` / `counter(pages)` | ✅ |
| `border-radius`, `opacity` | ✅ |
| Polices TTF/OTF embarquées | ✅ |

### Non supporté

| Feature | Alternative |
|---|---|
| Flexbox / Grid | `display: table` |
| CSS custom properties (`--var`) | Valeurs statiques |
| WOFF / WOFF2 | TTF/OTF uniquement |
| `@font-face` via CDN | Classpath local uniquement |

### Pattern en-tête / pied de page

```css
#entete     { position: fixed; top: -20mm; left: 0; right: 0; height: 22mm; }
#pied       { position: fixed; bottom: -14mm; left: 0; right: 0; height: 10mm; }
#pied:after { content: "Page " counter(page) " / " counter(pages); }
```

---

## Performances

### Ordres de grandeur (JVM chaud, `ITextRenderer` en pool)

| Document | Pages | Temps estimé |
|---|---|---|
| Courrier simple | 1–2 | 50–100 ms |
| Facture avec tableau | 1–3 | 100–200 ms |
| Rapport tabulaire dense | 5–15 | 200–500 ms |
| Document avec images | 2–5 | 150–400 ms |
| Rapport long | 20–50 | 500 ms – 2 s |

### Optimisations clés

- `ITextRenderer` **non thread-safe** → pool `ThreadLocal`
- Images embarquées en **base64 inline** (évite les I/O par génération)
- Tableaux longs : fragmenter en sections plutôt qu'un seul tableau monolithique
- Pour du batch : `ExecutorService` parallèle dimensionné selon les CPU disponibles

---

## Preview des documents

### Stratégie retenue : hybride HTML + PDF inline

| Mode | Mécanisme | Usage |
|---|---|---|
| Preview HTML temps réel | Endpoint `/preview` → HTML + CSS print-preview | Pendant la saisie |
| Aperçu PDF | Endpoint `/pdf` → `Content-Disposition: inline` dans `<iframe>` | Validation finale |

### CSS print-preview (simulation A4 en browser)

```css
@media screen {
    body    { background: #e0e0e0; padding: 20px; }
    .page   { background: white; width: 210mm; min-height: 297mm;
              margin: 0 auto 20px; padding: 20mm 15mm;
              box-shadow: 0 2px 8px rgba(0,0,0,0.2); }
}
```

### Rafraîchissement avec debounce (frontend)

```javascript
let debounceTimer;
inputs.forEach(el => el.addEventListener('input', () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => refreshPreview(), 400);
}));
```

---

## Stockage des documents générés

**Recommandation : MinIO** (compatible S3, self-hosted)

- Auto-hébergeable, licence Apache 2.0
- API S3 standard → migration AWS S3 transparente si nécessaire
- Intégration Spring via `software.amazon.awssdk:s3`

Alternative : stockage filesystem local pour les environnements simples.

---

## Port domaine

```java
// domain/document/port/out
public interface PdfRenderer {
    byte[] render(String html);
}

public interface DocumentStoragePort {
    String store(byte[] content);
    byte[] retrieve(String documentId);
}
```
