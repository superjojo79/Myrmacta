import type { FieldDefinition, FieldType } from '@/api/types'
import { i18n } from '@/i18n'

const m = (key: string, params: Record<string, unknown> = {}): string => i18n.global.t(`dossier.fieldValidation.${key}`, params)

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
// TODO §80 (D1, règles) — miroir de FieldDefinition : un champ calculé est en lecture seule, toujours présent.
const MAX_EXPRESSION_CHARS = 2000
// TODO §85 (LC4a) — miroir de FieldDefinition : aide contextuelle en texte brut.
const MAX_HELP_TEXT_CHARS = 500
// TODO §102 (U2, U3, U13) — miroir de FieldDefinition.UNIQUE_TYPES et MAX_UNIQUE_NAME_CHARS.
const UNIQUE_TYPES: FieldType[] = ['STRING', 'NUMBER', 'DATE']
const MAX_UNIQUE_NAME_CHARS = 100

/**
 * Client-side mirror of DossierFieldValidator.matchesType (immediate feedback) plus the
 * structural checks server validation doesn't cover at all (name uniqueness — D-NAME-UNIQUE is
 * enforced by DossierType, but only once submitted). Remplacement intégral : this validates the
 * whole proposed list, not a diff.
 */
export function validateFieldDefinitions(definitions: FieldDefinition[], owner?: string): string[] {
  const errors: string[] = []
  const seenNames = new Set<string>()
  const where = owner ? m('inGroup', { owner }) : ''

  for (const def of definitions) {
    const name = def.name?.trim() ?? ''
    const shown = name || m('unnamed')
    if (isBlank(name)) {
      errors.push(m('nameRequired', { where }))
    } else if (seenNames.has(name)) {
      errors.push(m('nameDuplicate', { name, where }))
    } else {
      seenNames.add(name)
    }

    if (isBlank(def.label)) {
      errors.push(m('labelRequired', { shown, where }))
    }

    if ((def.helpText ?? '').trim().length > MAX_HELP_TEXT_CHARS) {
      errors.push(m('helpTooLong', { shown, max: MAX_HELP_TEXT_CHARS }))
    }
    // Miroir du serveur : texte brut, sans contrôle (hors saut de ligne), marque bidirectionnelle ni espace de largeur nulle.
    if (/[\u0000-\u0009\u000B-\u001F\u007F-\u009F\u200B-\u200F\u202A-\u202E\u2066-\u2069\uFEFF]/.test(def.helpText ?? '')) {
      errors.push(m('helpForbidden', { shown }))
    }
    if (def.width === 'HALF' && def.type === 'REPEATABLE_GROUP') {
      errors.push(m('groupFullWidth', { shown }))
    }

    if (def.type === 'SELECT' && (def.options ?? []).length === 0) {
      errors.push(m('selectNeedsOption', { shown }))
    }

    if (def.type === 'MONEY') {
      for (const code of def.options ?? []) {
        if (!/^[A-Z]{3}$/.test(code)) {
          errors.push(m('badCurrency', { code, shown }))
        }
      }
    }
    if (def.type === 'EXTERNAL_REF') {
      for (const system of def.options ?? []) {
        if (!/^[A-Za-z0-9_.-]{1,50}$/.test(system)) {
          errors.push(m('badSystem', { system, shown }))
        }
      }
    }

    if (NO_DEFAULT_TYPES.includes(def.type) && !isBlank(def.defaultValue)) {
      errors.push(m('noDefault', { shown, type: def.type }))
    } else if (!defaultValueMatchesType(def)) {
      errors.push(m('defaultMismatch', { shown, type: def.type }))
    }

    for (const [label, expression] of [
      [m('ruleFormula'), def.formula],
      [m('ruleVisible'), def.visibleWhen],
      [m('ruleRequired'), def.requiredWhen],
    ] as const) {
      if (!isBlank(expression) && (expression as string).length > MAX_EXPRESSION_CHARS) {
        errors.push(m('expressionTooLong', { label, shown, max: MAX_EXPRESSION_CHARS }))
      }
    }
    if (def.searchable) {
      if (def.sensitive) errors.push(m('sensitiveSearchable', { shown }))
      if (!['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT'].includes(def.type)) {
        errors.push(m('typeNotSearchable', { type: def.type, shown }))
      }
    }
    if (def.unique) {
      if (owner) errors.push(m('subUnique', { shown, where }))
      if (def.sensitive) errors.push(m('sensitiveUnique', { shown }))
      if (!UNIQUE_TYPES.includes(def.type)) errors.push(m('typeNotUnique', { type: def.type, shown }))
      if (name.length > MAX_UNIQUE_NAME_CHARS) errors.push(m('uniqueNameTooLong', { shown, max: MAX_UNIQUE_NAME_CHARS }))
    }
    if (!isBlank(def.formula)) {
      if (def.sensitive) errors.push(m('computedSensitive', { shown }))
      if (def.type === 'REPEATABLE_GROUP' || def.type === 'FILE') {
        errors.push(m('typeNotComputed', { type: def.type, shown }))
      }
      if (def.required || !isBlank(def.defaultValue) || !isBlank(def.visibleWhen) || !isBlank(def.requiredWhen)) {
        errors.push(m('computedReadOnly', { shown }))
      }
    }
    if (owner && (!isBlank(def.formula) || !isBlank(def.visibleWhen) || !isBlank(def.requiredWhen))) {
      errors.push(m('subRule', { shown, where }))
    }

    if (def.type === 'REPEATABLE_GROUP') {
      const subs = def.fields ?? []
      if (subs.length === 0) {
        errors.push(m('groupNeedsSub', { shown }))
      }
      if (subs.length > MAX_GROUP_FIELDS) {
        errors.push(m('groupMaxSubs', { shown, max: MAX_GROUP_FIELDS }))
      }
      for (const sub of subs) {
        if (!GROUP_SUBFIELD_TYPES.includes(sub.type)) {
          errors.push(m('subType', { type: sub.type, sub: sub.name || m('unnamed') }))
        }
        if (!isBlank(sub.defaultValue)) {
          errors.push(m('subDefault', { sub: sub.name || m('unnamed') }))
        }
        if (sub.sensitive) {
          errors.push(m('subSensitive', { sub: sub.name || m('unnamed'), shown }))
        }
      }
      errors.push(...validateFieldDefinitions(subs, shown))
      const min = def.minRows ?? 0
      const max = def.maxRows ?? 100
      if (def.options && def.options.length > 0) {
        errors.push(m('groupNoOptions', { shown }))
      }
      if (max < 1) errors.push(m('maxRowsMin', { shown }))
      if (min < 0) errors.push(m('minRowsNegative', { shown }))
      if (max > HARD_MAX_ROWS) errors.push(m('maxRowsHard', { shown, max: HARD_MAX_ROWS }))
      if (min > max) errors.push(m('minAboveMax', { shown }))
    }
  }

  return errors
}
