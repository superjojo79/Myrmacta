import { isIdentifier, isVariablePath } from './variableCatalog'

/**
 * TODO §84 (LC4b, D-TPLED-ROUNDTRIP) — un template HTML existant n'est ouvert dans l'éditeur visuel que s'il appartient au
 * sous-ensemble que l'éditeur sait écrire SANS rien perdre : sinon il reste en mode source (aucune mise en forme n'est abîmée en
 * silence). Le sous-ensemble est celui que Flying Saucer (CSS 2.1) rend, plus trois constructions Thymeleaf simples.
 */
export interface SubsetAnalysis {
  compatible: boolean
  reasons: string[]
}

const ALLOWED_TAGS = new Set([
  'p', 'h1', 'h2', 'h3', 'strong', 'b', 'em', 'i', 'u', 'ul', 'ol', 'li', 'br', 'hr',
  'table', 'tbody', 'thead', 'tr', 'td', 'th', 'div', 'span',
])

const ALIGN = /^text-align:\s*(left|center|right|justify);?$/i
const PAGE_BREAK = /^page-break-after:\s*always;?$/i
const TH_TEXT = /^\$\{([^{}]*)}$/
const TH_EACH = /^(\S+?)\s*:\s*\$\{([^{}]*)\}$/u

/** Valeur littérale d'un `#strings.equals(chemin,'valeur')` sans caractère à échapper. */
const EQUALS = /^\$\{#strings\.equals\(\s*([^\s,()'"]+)\s*,\s*'([^'\\${}]*)'\s*\)}$/

export function parseVariableExpression(value: string): string | null {
  const m = TH_TEXT.exec(value.trim())
  return m && isVariablePath(m[1]!.trim()) ? m[1]!.trim() : null
}

export type Condition =
  | { mode: 'if' | 'unless'; path: string }
  | { mode: 'equals'; path: string; value: string }

export function parseCondition(attr: 'th:if' | 'th:unless', value: string): Condition | null {
  const trimmed = value.trim()
  const simple = parseVariableExpression(trimmed)
  if (simple) return { mode: attr === 'th:if' ? 'if' : 'unless', path: simple }
  if (attr === 'th:if') {
    const m = EQUALS.exec(trimmed)
    if (m && isVariablePath(m[1]!)) return { mode: 'equals', path: m[1]!, value: m[2]! }
  }
  return null
}

export function conditionAttributes(c: Condition): Record<string, string> {
  if (c.mode === 'unless') return { 'th:unless': `\${${c.path}}` }
  if (c.mode === 'equals') return { 'th:if': `\${#strings.equals(${c.path},'${c.value}')}` }
  return { 'th:if': `\${${c.path}}` }
}

export function parseEach(value: string): { item: string; path: string } | null {
  const m = TH_EACH.exec(value.trim())
  return m && isIdentifier(m[1]!) && isVariablePath(m[2]!.trim()) ? { item: m[1]!, path: m[2]!.trim() } : null
}

export function eachAttribute(item: string, path: string): string {
  return `${item} : \${${path}}`
}

/** Valeur d'égalité acceptable : ni apostrophe, ni antislash, ni accolade, ni dollar (pas d'échappement à écrire). */
export function isSafeLiteral(value: string): boolean {
  return /^[^'\\${}\r\n]{0,200}$/.test(value)
}

/**
 * `render` (facultatif) fait passer le HTML par l'éditeur et rend ce qu'il écrirait : quand l'analyse statique est favorable, on
 * vérifie que rien de significatif n'est perdu (revue adversariale LC4b) — l'analyse statique juge chaque attribut isolément et ne
 * voit ni les combinaisons ni les structures aplaties par le schéma de l'éditeur ; seul le tour complet le garantit.
 */
export function analyzeTemplateHtml(html: string, render?: (html: string) => string): SubsetAnalysis {
  const reasons: string[] = []
  const doc = new DOMParser().parseFromString(`<!doctype html><body>${html}</body>`, 'text/html')
  const root = doc.body
  if (/<(html|head|body)[\s>]/i.test(html)) {
    reasons.push('document complet (html, head ou body)')
  }
  walk(root, reasons)
  if (reasons.length === 0 && render) {
    reasons.push(...roundTripLosses(html, render(html)))
  }
  return { compatible: reasons.length === 0, reasons: [...new Set(reasons)] }
}

const COUNTED_TAGS = ['table', 'thead', 'tbody', 'tr', 'td', 'th', 'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'hr', 'br']
const TAG_ALIASES: Record<string, string> = { b: 'strong', i: 'em' }

/** Empreinte de ce qui compte à l'arrivée : contenu, structure de tableau et de liste, mise en forme, constructions th:. */
function signature(html: string): { text: string; items: Map<string, number> } {
  const doc = new DOMParser().parseFromString(`<!doctype html><body>${html}</body>`, 'text/html')
  const items = new Map<string, number>()
  const bump = (key: string) => items.set(key, (items.get(key) ?? 0) + 1)
  for (const el of Array.from(doc.body.querySelectorAll('*'))) {
    const raw = el.tagName.toLowerCase()
    const tag = TAG_ALIASES[raw] ?? raw
    if (COUNTED_TAGS.includes(tag) || tag === 'strong' || tag === 'em' || tag === 'u') bump(`<${tag}>`)
    for (const attr of Array.from(el.attributes)) {
      const name = attr.name.toLowerCase()
      if (name === 'style') bump(`style=${attr.value.toLowerCase().replace(/\s+/g, '')}`)
      else bump(`${name}=${attr.value.replace(/\s+/g, ' ').trim()}`)
    }
  }
  // Le corps d'un th:text n'est qu'un texte d'exemple, remplacé au rendu (l'éditeur y écrit « chemin ») : il ne compte pas.
  for (const el of Array.from(doc.body.querySelectorAll('*'))) {
    if (el.hasAttribute('th:text')) el.textContent = ''
  }
  const text = (doc.body.textContent ?? '').replace(/\s+/g, ' ').trim()
  return { text, items }
}

/** Ce que l'éditeur perdrait ou changerait en écrivant `after` à la place de `before` (liste vide = restitution fidèle). */
export function roundTripLosses(before: string, after: string): string[] {
  const a = signature(before)
  const b = signature(after)
  const losses: string[] = []
  if (a.text !== b.text) losses.push('le texte serait modifié par l’éditeur')
  const keys = new Set([...a.items.keys(), ...b.items.keys()])
  for (const key of keys) {
    if ((a.items.get(key) ?? 0) !== (b.items.get(key) ?? 0)) {
      losses.push(`l’éditeur ne restitue pas fidèlement « ${key.length > 80 ? key.slice(0, 80) + '…' : key} »`)
    }
  }
  return losses
}

function walk(node: Node, reasons: string[]): void {
  for (const child of Array.from(node.childNodes)) {
    if (child.nodeType === Node.COMMENT_NODE) {
      reasons.push('commentaire HTML')
      continue
    }
    if (child.nodeType === Node.TEXT_NODE) {
      // Texte décodé (les entités &#91; &#91; deviendraient des expressions vives une fois réécrites) : jamais d'expression en ligne.
      const text = child.textContent ?? ''
      if (/\[\[|\[\(/.test(text)) reasons.push('expression en ligne dans le texte ([[…]])')
      if (/\$\{|\*\{/.test(text)) reasons.push('expression ${…} dans le texte (hors attribut th:)')
      continue
    }
    if (child.nodeType !== Node.ELEMENT_NODE) continue
    const el = child as Element
    const tag = el.tagName.toLowerCase()
    if (!ALLOWED_TAGS.has(tag)) {
      reasons.push(`balise <${tag}> non prise en charge`)
      continue
    }
    const exclusive = Array.from(el.attributes).filter((a) => ['th:if', 'th:unless', 'th:each', 'style'].includes(a.name.toLowerCase()))
    if (exclusive.length > 1) {
      reasons.push(`<${tag}> cumule ${exclusive.map((a) => a.name.toLowerCase()).join(' + ')} (un seul à la fois)`)
    }
    for (const attr of Array.from(el.attributes)) {
      const problem = attributeProblem(tag, attr.name.toLowerCase(), attr.value)
      if (problem) reasons.push(problem)
    }
    walk(el, reasons)
  }
}

function attributeProblem(tag: string, name: string, value: string): string | null {
  switch (name) {
    case 'style':
      if (ALIGN.test(value.trim()) && ['p', 'h1', 'h2', 'h3', 'td', 'th'].includes(tag)) return null
      if (PAGE_BREAK.test(value.trim()) && tag === 'div') return null
      return `style « ${value} » sur <${tag}> non pris en charge`
    case 'colspan':
    case 'rowspan':
      return tag === 'td' || tag === 'th' ? null : `attribut ${name} sur <${tag}>`
    case 'th:text':
      return tag === 'span' && parseVariableExpression(value) ? null : `th:text « ${value} » non pris en charge (chemin simple dans un <span> uniquement)`
    case 'th:if':
    case 'th:unless':
      return tag === 'div' && parseCondition(name, value) ? null : `${name} « ${value} » non pris en charge`
    case 'th:each':
      return (tag === 'div' || tag === 'tr') && parseEach(value) ? null : `th:each « ${value} » non pris en charge`
    default:
      return `attribut ${name} sur <${tag}> non pris en charge`
  }
}
