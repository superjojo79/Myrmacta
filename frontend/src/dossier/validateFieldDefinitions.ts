import type { FieldDefinition } from '@/api/types'

function isBlank(value: string | undefined | null): boolean {
  return value == null || value.trim() === ''
}

function defaultValueMatchesType(def: FieldDefinition): boolean {
  const value = def.defaultValue
  if (value == null || value === '') return true
  switch (def.type) {
    case 'NUMBER':
      return !Number.isNaN(Number(value))
    case 'BOOLEAN':
      return value === 'true' || value === 'false'
    case 'DATE':
      return !Number.isNaN(Date.parse(value)) && /^\d{4}-\d{2}-\d{2}/.test(value)
    default:
      return true
  }
}

/**
 * Client-side mirror of DossierFieldValidator.matchesType (immediate feedback) plus the
 * structural checks server validation doesn't cover at all (name uniqueness — D-NAME-UNIQUE is
 * enforced by DossierType, but only once submitted). Remplacement intégral : this validates the
 * whole proposed list, not a diff.
 */
export function validateFieldDefinitions(definitions: FieldDefinition[]): string[] {
  const errors: string[] = []
  const seenNames = new Set<string>()

  for (const def of definitions) {
    const name = def.name?.trim() ?? ''
    if (isBlank(name)) {
      errors.push('Le nom du champ est obligatoire')
    } else if (seenNames.has(name)) {
      errors.push(`Le nom de champ « ${name} » est utilisé plusieurs fois`)
    } else {
      seenNames.add(name)
    }

    if (isBlank(def.label)) {
      errors.push(`Le libellé du champ « ${name || '(sans nom)'} » est obligatoire`)
    }

    if (def.type === 'SELECT' && (def.options ?? []).length === 0) {
      errors.push(`Le champ « ${name || '(sans nom)'} » de type SELECT doit avoir au moins une option`)
    }

    if (!defaultValueMatchesType(def)) {
      errors.push(`La valeur par défaut du champ « ${name || '(sans nom)'} » ne correspond pas au type ${def.type}`)
    }
  }

  return errors
}
