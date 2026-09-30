import type { FieldDefinition, FieldType } from '@/api/types'

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

// TODO §76 (D1) — règles des types structurés, miroir de FieldDefinition (serveur) pour un retour immédiat.
const GROUP_SUBFIELD_TYPES: FieldType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT', 'MONEY', 'EXTERNAL_REF']
const NO_DEFAULT_TYPES: FieldType[] = ['MONEY', 'EXTERNAL_REF', 'REPEATABLE_GROUP']
const MAX_GROUP_FIELDS = 20
const HARD_MAX_ROWS = 500

/**
 * Client-side mirror of DossierFieldValidator.matchesType (immediate feedback) plus the
 * structural checks server validation doesn't cover at all (name uniqueness — D-NAME-UNIQUE is
 * enforced by DossierType, but only once submitted). Remplacement intégral : this validates the
 * whole proposed list, not a diff.
 */
export function validateFieldDefinitions(definitions: FieldDefinition[], owner?: string): string[] {
  const errors: string[] = []
  const seenNames = new Set<string>()
  const where = owner ? ` (groupe « ${owner} »)` : ''

  for (const def of definitions) {
    const name = def.name?.trim() ?? ''
    const shown = name || '(sans nom)'
    if (isBlank(name)) {
      errors.push(`Le nom du champ est obligatoire${where}`)
    } else if (seenNames.has(name)) {
      errors.push(`Le nom de champ « ${name} » est utilisé plusieurs fois${where}`)
    } else {
      seenNames.add(name)
    }

    if (isBlank(def.label)) {
      errors.push(`Le libellé du champ « ${shown} » est obligatoire${where}`)
    }

    if (def.type === 'SELECT' && (def.options ?? []).length === 0) {
      errors.push(`Le champ « ${shown} » de type SELECT doit avoir au moins une option`)
    }

    if (def.type === 'MONEY') {
      for (const code of def.options ?? []) {
        if (!/^[A-Z]{3}$/.test(code)) {
          errors.push(`La devise « ${code} » du champ « ${shown} » n'est pas un code ISO 4217 en majuscules (ex. EUR)`)
        }
      }
    }
    if (def.type === 'EXTERNAL_REF') {
      for (const system of def.options ?? []) {
        if (!/^[A-Za-z0-9_.-]{1,50}$/.test(system)) {
          errors.push(`Le système « ${system} » du champ « ${shown} » n'est pas un identifiant valide`)
        }
      }
    }

    if (NO_DEFAULT_TYPES.includes(def.type) && !isBlank(def.defaultValue)) {
      errors.push(`Le champ « ${shown} » (${def.type}) ne peut pas avoir de valeur par défaut`)
    } else if (!defaultValueMatchesType(def)) {
      errors.push(`La valeur par défaut du champ « ${shown} » ne correspond pas au type ${def.type}`)
    }

    if (def.type === 'REPEATABLE_GROUP') {
      const subs = def.fields ?? []
      if (subs.length === 0) {
        errors.push(`Le groupe « ${shown} » doit avoir au moins un sous-champ`)
      }
      if (subs.length > MAX_GROUP_FIELDS) {
        errors.push(`Le groupe « ${shown} » a au plus ${MAX_GROUP_FIELDS} sous-champs`)
      }
      for (const sub of subs) {
        if (!GROUP_SUBFIELD_TYPES.includes(sub.type)) {
          errors.push(`Le type ${sub.type} du sous-champ « ${sub.name || '(sans nom)'} » n'est pas autorisé dans un groupe`)
        }
        if (!isBlank(sub.defaultValue)) {
          errors.push(`Le sous-champ « ${sub.name || '(sans nom)'} » ne peut pas avoir de valeur par défaut (elle ne serait jamais appliquée)`)
        }
        if (sub.sensitive) {
          errors.push(`Le sous-champ « ${sub.name || '(sans nom)'} » ne peut pas être sensible : marquez le groupe « ${shown} » lui-même`)
        }
      }
      errors.push(...validateFieldDefinitions(subs, shown))
      const min = def.minRows ?? 0
      const max = def.maxRows ?? 100
      if (def.options && def.options.length > 0) {
        errors.push(`Le groupe « ${shown} » n'accepte pas d'options`)
      }
      if (max < 1) errors.push(`Le nombre maximal de lignes du groupe « ${shown} » est au moins 1`)
      if (min < 0) errors.push(`Le nombre minimal de lignes du groupe « ${shown} » ne peut pas être négatif`)
      if (max > HARD_MAX_ROWS) errors.push(`Le nombre maximal de lignes du groupe « ${shown} » est ${HARD_MAX_ROWS}`)
      if (min > max) errors.push(`Le minimum de lignes du groupe « ${shown} » dépasse son maximum`)
    }
  }

  return errors
}
