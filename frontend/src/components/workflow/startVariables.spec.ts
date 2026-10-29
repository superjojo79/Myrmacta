import { describe, expect, it } from 'vitest'
import { collectDeclaredStartVariables, RESERVED_START_VARIABLES, validateStartVariables } from './startVariables'

describe('validateStartVariables (TODO §93)', () => {
  it('construit les variables texte et ignore les lignes vides', () => {
    const result = validateStartVariables([
      { name: ' priorite ', value: 'haute' },
      { name: '', value: '' },
      { name: 'notificationTo', value: ' a@example.org ' },
    ])
    expect(result.errors).toEqual({})
    expect(result.variables).toEqual({ priorite: 'haute', notificationTo: ' a@example.org ' })
  })

  it('sans ligne, renvoie un objet vide', () => {
    expect(validateStartVariables([])).toEqual({ variables: {}, errors: {} })
  })

  it('refuse un nom invalide ou absent avec une valeur', () => {
    const result = validateStartVariables([
      { name: '1x', value: 'a' },
      { name: 'a.b', value: 'a' },
      { name: '', value: 'orpheline' },
    ])
    expect(result.errors).toEqual({ 0: 'invalid', 1: 'invalid', 2: 'invalid' })
  })

  it('refuse les noms réservés de la plateforme', () => {
    expect(RESERVED_START_VARIABLES).toEqual([
      'dossierId',
      'dossierWorkflowLinkId',
      'tenantId',
      'dossierFields',
      'connectorResult',
      'generatedDocumentId',
    ])
    const rows = RESERVED_START_VARIABLES.map((name) => ({ name, value: 'x' }))
    expect(Object.values(validateStartVariables(rows).errors)).toEqual(
      RESERVED_START_VARIABLES.map(() => 'reserved'),
    )
  })

  it('refuse un doublon sur la seconde occurrence', () => {
    const result = validateStartVariables([
      { name: 'priorite', value: 'haute' },
      { name: 'priorite', value: 'basse' },
    ])
    expect(result.errors).toEqual({ 1: 'duplicate' })
  })

  it('accepte une valeur vide pour un nom renseigné', () => {
    expect(validateStartVariables([{ name: 'commentaire', value: '' }]).variables).toEqual({
      commentaire: '',
    })
  })
})

describe('collectDeclaredStartVariables (TODO §111)', () => {
  const declarations = [
    { name: 'motif', label: 'Motif', required: true },
    { name: 'note', label: 'Note', required: false },
  ]

  it('envoie le texte rogné et omet une facultative blanche', () => {
    expect(collectDeclaredStartVariables(declarations, { motif: '  travaux ', note: '  ' })).toEqual({
      variables: { motif: 'travaux' },
      missing: [],
    })
  })

  it('signale une obligatoire vide et n’envoie rien', () => {
    const result = collectDeclaredStartVariables(declarations, { motif: ' ', note: 'vue' })
    expect(result.missing.map((item) => item.name)).toEqual(['motif'])
    expect(result.variables).toEqual({})
  })
})
