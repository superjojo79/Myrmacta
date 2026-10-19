import { describe, expect, it } from 'vitest'
import { insertAt, parseFieldMapping, serializeFieldMapping } from './fieldMapping'

describe('fieldMapping (format « champ = expression », une ligne par champ)', () => {
  it('splits on the first = that is not part of ==, keeping the expression intact', () => {
    expect(parseFieldMapping('score = connectorResult.json.score\neligible = dossier.fields.montant = 5 ? "oui" : "non"')).toEqual([
      { field: 'score', expression: 'connectorResult.json.score' },
      { field: 'eligible', expression: 'dossier.fields.montant = 5 ? "oui" : "non"' },
    ])
  })

  it('ignores blank lines and keeps a line without = as a field with no expression', () => {
    expect(parseFieldMapping('\n  adresse  \n\n')).toEqual([{ field: 'adresse', expression: '' }])
    expect(parseFieldMapping('')).toEqual([])
    expect(parseFieldMapping(undefined)).toEqual([])
  })

  it('serializes back, dropping fully empty rows, and round-trips', () => {
    const text = 'score = connectorResult.json.score\nadresse = dossier.fields.rue & " " & dossier.fields.ville'
    expect(serializeFieldMapping(parseFieldMapping(text))).toBe(text)
    expect(serializeFieldMapping([{ field: ' a ', expression: ' 1 ' }, { field: '', expression: '' }])).toBe('a = 1')
  })

  it('inserts a snippet at the caret, padding with a space when glued to a word', () => {
    expect(insertAt('dossier.fields.a >', 18, 18, '5')).toEqual({ text: 'dossier.fields.a > 5', caret: 20 })
    expect(insertAt('', 0, 0, 'dossier.fields.a')).toEqual({ text: 'dossier.fields.a', caret: 16 })
    expect(insertAt('a  b', 2, 2, 'and')).toEqual({ text: 'a and b', caret: 5 })
    expect(insertAt('x(', 2, 2, 'y')).toEqual({ text: 'x(y', caret: 3 })
  })
})
