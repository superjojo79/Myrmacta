/**
 * TODO §90 (D7) — détecteur de texte en dur dans un template Vue : texte entre balises contenant des lettres, et attributs
 * d'accessibilité ou d'aide (placeholder, title, aria-label, alt, label). Sert au test de couverture (i18nCoverage.spec.ts) :
 * il n'interprète pas le code, il repère ce qu'un lecteur étranger verrait en français.
 */
const ATTRS = /\s(?:placeholder|title|aria-label|aria-description|aria-placeholder|alt|label)=(?:"([^"]*[A-Za-zÀ-ÿ][^"]*)"|'([^']*[A-Za-zÀ-ÿ][^']*)')/g
const TEXT = />([^<>]*[A-Za-zÀ-ÿ][^<>]*)</g

export function templateOf(source: string): string {
  const start = source.indexOf('<template>')
  const end = source.lastIndexOf('</template>')
  return start === -1 || end === -1 ? '' : source.slice(start + '<template>'.length, end)
}

function visible(text: string): string {
  return text
    .replace(/\{\{[\s\S]*?\}\}/g, '') // interpolations : du code, pas du texte
    .replace(/&(?:nbsp|amp|lt|gt|quot|times|rarr|larr|hellip|mdash|ndash|laquo|raquo);|&#\d+;/g, ' ')
    .trim()
}

/** Textes visibles écrits en dur (hors interpolations), vides exclus. */
export function rawTexts(source: string): string[] {
  const template = templateOf(source).replace(/<!--[\s\S]*?-->/g, '')
  const found: string[] = []
  for (const match of template.matchAll(TEXT)) {
    const text = visible(match[1])
    if (/[A-Za-zÀ-ÿ]{2,}/.test(text)) found.push(text.replace(/\s+/g, ' '))
  }
  for (const match of template.matchAll(ATTRS)) {
    // une valeur liée (:placeholder="…") n'est pas un texte ; l'attribut statique l'est
    const text = visible(match[1] ?? match[2] ?? '')
    if (/[A-Za-zÀ-ÿ]{2,}/.test(text) && !/^\s*[$\w.]+\(/.test(text)) found.push(text)
  }
  return found
}
