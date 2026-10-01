import { describe, expect, it } from 'vitest'
import { buildVariableCatalog, isIdentifier, isVariablePath } from './variableCatalog'

describe('variableCatalog (TODO §84, LC4b)', () => {
  it('lists scalar paths and repeatable lists with their item fields', () => {
    const catalog = buildVariableCatalog({
      title: 'Facture',
      client: { nom: 'ACME', adresse: { ville: 'Lyon' } },
      lignes: [{ designation: 'x', prix: 1 }, { designation: 'y' }],
      tags: ['a', 'b'],
      vide: [],
    })
    expect(catalog.scalars).toEqual(['client.adresse.ville', 'client.nom', 'title'])
    expect(catalog.lists).toEqual([
      { path: 'lignes', itemFields: ['designation', 'prix'] },
      { path: 'tags', itemFields: [] },
      { path: 'vide', itemFields: [] },
    ])
  })

  it('ignores keys the expression allowlist cannot address and bounds depth and size', () => {
    const catalog = buildVariableCatalog({ 'bad key': 1, 'a-b': 2, ok: 3 })
    expect(catalog.scalars).toEqual(['ok'])
    const deep = { a: { b: { c: { d: { e: { f: { g: 1 } } } } } } }
    expect(buildVariableCatalog(deep).scalars).toEqual([])
    const many = Object.fromEntries(Array.from({ length: 500 }, (_, i) => [`k${i}`, i]))
    expect(buildVariableCatalog(many).scalars.length).toBeLessThanOrEqual(200)
    expect(buildVariableCatalog(null)).toEqual({ scalars: [], lists: [] })
    expect(buildVariableCatalog([1, 2])).toEqual({ scalars: [], lists: [] })
  })

  it('validates identifiers and variable paths', () => {
    expect(isIdentifier('client')).toBe(true)
    expect(isIdentifier('1a')).toBe(false)
    expect(isVariablePath('client.adresse.ville')).toBe(true)
    for (const bad of ['', 'a.', '.a', 'a..b', 'a b', 'a()', 'a[0]', 'a-b', "a'b", '${x}', '#vars.x', 'a.b()']) {
      expect(isVariablePath(bad), bad).toBe(false)
    }
  })
})
