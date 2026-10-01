import { mergeAttributes, Node } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import TextAlign from '@tiptap/extension-text-align'
import { Table, TableCell, TableHeader, TableRow } from '@tiptap/extension-table'
import {
  conditionAttributes,
  eachAttribute,
  parseCondition,
  parseEach,
  parseVariableExpression,
  type Condition,
} from './htmlSubset'

/**
 * TODO §84 (LC4b) — extensions TipTap : uniquement ce que Flying Saucer (CSS 2.1) rend et ce que l'allowlist des expressions accepte
 * (D-40, §40). Chaque nœud s'écrit en HTML Thymeleaf minimal, sans attribut de service (`data-*`, classes) : le HTML reste le contrat
 * (P5, analyse 46) et un template écrit à la main dans ce sous-ensemble se relit sans perte.
 */

/** Variable : `<span th:text="${chemin}">«chemin»</span>` — le texte interne n'est qu'un repère visuel remplacé au rendu. */
export const Variable = Node.create({
  name: 'variable',
  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  addAttributes() {
    return { path: { default: '' } }
  },
  parseHTML() {
    return [
      {
        // sélecteur « span » + test d'attribut : `[th\\:text]` n'est pas portable (jsdom) et le résultat doit être identique partout
        tag: 'span',
        getAttrs: (el) => {
          const raw = (el as HTMLElement).getAttribute('th:text')
          const path = raw === null ? null : parseVariableExpression(raw)
          return path ? { path } : false
        },
      },
    ]
  },
  renderHTML({ node }) {
    const path = String(node.attrs.path)
    return ['span', { 'th:text': `\${${path}}` }, `«${path}»`]
  },
  renderText({ node }) {
    return `«${String(node.attrs.path)}»`
  },
})

interface ConditionAttrs {
  mode: Condition['mode']
  path: string
  value: string
}

function toCondition(attrs: ConditionAttrs): Condition {
  return attrs.mode === 'equals'
    ? { mode: 'equals', path: attrs.path, value: attrs.value }
    : { mode: attrs.mode, path: attrs.path }
}

/** Bloc conditionnel : `<div th:if="${chemin}">`, `th:unless`, ou `th:if="${#strings.equals(chemin,'x')}"`. */
export const ConditionalBlock = Node.create({
  name: 'conditionalBlock',
  group: 'block',
  content: 'block+',
  defining: true,
  addAttributes() {
    return { mode: { default: 'if' }, path: { default: '' }, value: { default: '' } }
  },
  parseHTML() {
    const read = (attr: 'th:if' | 'th:unless') => (el: Node | string) => {
      const raw = (el as HTMLElement).getAttribute(attr)
      const c = raw === null ? null : parseCondition(attr, raw)
      if (!c) return false
      return { mode: c.mode, path: c.path, value: c.mode === 'equals' ? c.value : '' }
    }
    return [
      { tag: 'div', getAttrs: read('th:if') },
      { tag: 'div', getAttrs: read('th:unless') },
    ]
  },
  renderHTML({ node }) {
    return ['div', conditionAttributes(toCondition(node.attrs as ConditionAttrs)), 0]
  },
})

/** Bloc répété : `<div th:each="item : ${liste}">` ; à l'intérieur, les variables s'écrivent `item.champ`. */
export const RepeatBlock = Node.create({
  name: 'repeatBlock',
  group: 'block',
  content: 'block+',
  defining: true,
  addAttributes() {
    return { item: { default: 'item' }, path: { default: '' } }
  },
  parseHTML() {
    return [
      {
        tag: 'div',
        getAttrs: (el) => {
          const raw = (el as HTMLElement).getAttribute('th:each')
          const each = raw === null ? null : parseEach(raw)
          return each ? { item: each.item, path: each.path } : false
        },
      },
    ]
  },
  renderHTML({ node }) {
    return ['div', { 'th:each': eachAttribute(String(node.attrs.item), String(node.attrs.path)) }, 0]
  },
})

/** Saut de page CSS 2.1 : `<div style="page-break-after: always"></div>`. */
export const PageBreak = Node.create({
  name: 'pageBreak',
  group: 'block',
  atom: true,
  selectable: true,
  parseHTML() {
    return [
      {
        tag: 'div',
        getAttrs: (el) => (/^page-break-after:\s*always;?$/i.test(((el as HTMLElement).getAttribute('style') ?? '').trim()) ? {} : false),
      },
    ]
  },
  renderHTML() {
    return ['div', { style: 'page-break-after: always' }]
  },
})

/** Ligne de tableau répétable : `<tr th:each="l : ${lignes}">`. */
export const RepeatableRow = TableRow.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      each: {
        default: null as { item: string; path: string } | null,
        parseHTML: (el: HTMLElement) => parseEach(el.getAttribute('th:each') ?? ''),
        renderHTML: (attrs: Record<string, unknown>) => {
          const each = attrs.each as { item: string; path: string } | null
          return each ? { 'th:each': eachAttribute(each.item, each.path) } : {}
        },
      },
    }
  },
})

/** Tableau sans style ajouté (`min-width`, `colgroup`) : seul ce que CSS 2.1 rend sans surprise. */
const PlainTable = Table.configure({ resizable: false, renderWrapper: false }).extend({
  renderHTML({ HTMLAttributes }) {
    return ['table', mergeAttributes(this.options.HTMLAttributes, HTMLAttributes), ['tbody', 0]]
  },
})

export function templateExtensions() {
  return [
    StarterKit.configure({
      // Hors sous-ensemble : pas de lien (URL scriptable), de code, de citation, de barré, ni de titres au-delà de 3.
      link: false,
      code: false,
      codeBlock: false,
      blockquote: false,
      strike: false,
      heading: { levels: [1, 2, 3] },
    }),
    TextAlign.configure({ types: ['heading', 'paragraph'], alignments: ['left', 'center', 'right', 'justify'] }),
    PlainTable,
    RepeatableRow,
    TableHeader,
    TableCell,
    Variable,
    ConditionalBlock,
    RepeatBlock,
    PageBreak,
  ]
}
