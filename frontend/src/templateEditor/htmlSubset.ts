import { isVariablePath } from './variableCatalog'

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
const TH_EACH = /^([A-Za-z_][A-Za-z0-9_]*)\s*:\s*\$\{([^{}]*)}$/

/** Valeur littérale d'un `#strings.equals(chemin,'valeur')` sans caractère à échapper. */
const EQUALS = /^\$\{#strings\.equals\(\s*([A-Za-z0-9_.]+)\s*,\s*'([^'\\${}]*)'\s*\)}$/

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
  return m && isVariablePath(m[2]!.trim()) ? { item: m[1]!, path: m[2]!.trim() } : null
}

export function eachAttribute(item: string, path: string): string {
  return `${item} : \${${path}}`
}

/** Valeur d'égalité acceptable : ni apostrophe, ni antislash, ni accolade, ni dollar (pas d'échappement à écrire). */
export function isSafeLiteral(value: string): boolean {
  return /^[^'\\${}\r\n]{0,200}$/.test(value)
}

export function analyzeTemplateHtml(html: string): SubsetAnalysis {
  const reasons: string[] = []
  if (/\[\[|\[\(/.test(html)) {
    reasons.push('expression en ligne dans le texte ([[…]])')
  }
  if (/\$\{|\*\{/.test(stripAttributeValues(html))) {
    reasons.push('expression ${…} dans le texte (hors attribut th:)')
  }
  const doc = new DOMParser().parseFromString(`<!doctype html><body>${html}</body>`, 'text/html')
  const root = doc.body
  if (/<(html|head|body)[\s>]/i.test(html)) {
    reasons.push('document complet (html, head ou body)')
  }
  walk(root, reasons)
  return { compatible: reasons.length === 0, reasons: [...new Set(reasons)] }
}

/** Retire les valeurs d'attributs entre guillemets pour ne chercher `${` que dans le texte. */
function stripAttributeValues(html: string): string {
  return html.replace(/"[^"]*"|'[^']*'/g, '""')
}

function walk(node: Node, reasons: string[]): void {
  for (const child of Array.from(node.childNodes)) {
    if (child.nodeType === Node.COMMENT_NODE) {
      reasons.push('commentaire HTML')
      continue
    }
    if (child.nodeType !== Node.ELEMENT_NODE) continue
    const el = child as Element
    const tag = el.tagName.toLowerCase()
    if (!ALLOWED_TAGS.has(tag)) {
      reasons.push(`balise <${tag}> non prise en charge`)
      continue
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
      if (ALIGN.test(value.trim()) && ['p', 'h1', 'h2', 'h3', 'td', 'th', 'li'].includes(tag)) return null
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
