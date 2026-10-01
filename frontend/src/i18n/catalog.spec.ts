import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { describe, expect, it } from 'vitest'
import { messages as portalMessages } from './messages/bundle.portal'
import en from './messages/en'
import fr from './messages/fr'

/** TODO §90 (D7) — garde-fous du catalogue : mêmes clés, mêmes paramètres, aucune clé utilisée sans texte. */
type Tree = { [key: string]: string | Tree }

function flatten(tree: Tree, prefix = ''): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'string') out[path] = value
    else Object.assign(out, flatten(value, path))
  }
  return out
}

const frFlat = flatten(fr as unknown as Tree)
const enFlat = flatten(en as unknown as Tree)
const params = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort()

describe('message catalog', () => {
  it('French and English have exactly the same keys', () => {
    expect(Object.keys(enFlat).sort()).toEqual(Object.keys(frFlat).sort())
  })

  it('no text is empty (an empty text hides a missing translation)', () => {
    for (const [key, text] of [...Object.entries(frFlat), ...Object.entries(enFlat)]) {
      if (key === 'common.none') continue
      expect(text.trim(), key).not.toBe('')
    }
  })

  it('both languages use the same {placeholders}', () => {
    for (const key of Object.keys(frFlat)) {
      expect(params(enFlat[key]), key).toEqual(params(frFlat[key]))
    }
  })

  it('English differs from French for sentences (a copied French text is a missing translation)', () => {
    const identicalAllowed = /^(common\.languages\.|common\.none|.*\.(brand|reference|code|id)$)/
    const words = (text: string) => text.replace(/\{\w+\}/g, ' ').split(/\s+/).filter((w) => /[A-Za-zÀ-ÿ]{2,}/.test(w)).length
    const copied = Object.keys(frFlat).filter((k) => frFlat[k] === enFlat[k] && words(frFlat[k]) >= 3 && !identicalAllowed.test(k))
    expect(copied).toEqual([])
  })
})

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return name === 'node_modules' || name === 'test' ? [] : sources(path)
    return /\.(vue|ts)$/.test(name) && !/\.(spec|test)\.ts$/.test(name) ? [path] : []
  })
}

describe('key usage', () => {
  it('every literal key passed to t() / $t() / tc() exists in the French catalog', () => {
    const root = join(__dirname, '..', '..')
    const files = [...sources(join(root, 'src')), ...sources(join(root, 'portal'))]
    const missing: string[] = []
    for (const file of files) {
      const text = readFileSync(file, 'utf8')
      for (const match of text.matchAll(/(?<![\w.])\$?t\(\s*'([A-Za-z][\w.]*)'/g)) {
        if (!(match[1] in frFlat)) missing.push(`${relative(root, file)}: ${match[1]}`)
      }
    }
    expect(missing).toEqual([])
  })
})

describe('portal bundle', () => {
  it('holds every key used by the portal pages and by the shared field input', () => {
    const root = join(__dirname, '..', '..')
    const portalFlat = flatten(portalMessages.fr as unknown as Tree)
    const files = [...sources(join(root, 'portal')), join(root, 'src', 'components', 'dossier', 'DynamicFieldInput.vue'), join(root, 'src', 'i18n', 'LocaleSwitcher.vue')]
    const missing: string[] = []
    for (const file of files) {
      const text = readFileSync(file, 'utf8')
      for (const match of text.matchAll(/(?<![\w.])\$?t\(\s*'([A-Za-z][\w.]*)'/g)) {
        if (!(match[1] in portalFlat)) missing.push(`${relative(root, file)}: ${match[1]}`)
      }
      for (const match of text.matchAll(/t\(`([A-Za-z][\w.]*)\.\$\{/g)) {
        if (!Object.keys(portalFlat).some((k) => k.startsWith(match[1] + '.'))) missing.push(`${relative(root, file)}: ${match[1]}.*`)
      }
    }
    expect(missing).toEqual([])
  })
})
