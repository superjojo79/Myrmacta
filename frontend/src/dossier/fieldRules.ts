import type { FieldDefinition, FieldRulesPreview } from '@/api/types'

/** Vrai si au moins un champ du type porte une règle (calcul, visibilité, obligation conditionnelle). */
export function hasFieldRules(definitions: FieldDefinition[]): boolean {
  return definitions.some((d) => !!d.formula || !!d.visibleWhen || !!d.requiredWhen)
}

export function isComputedField(def: FieldDefinition): boolean {
  return !!def.formula
}

/** Champ applicable ? Sans aperçu des règles (pas encore reçu, type sans règle) : toujours oui. */
export function isFieldApplicable(def: FieldDefinition, rules: FieldRulesPreview | null): boolean {
  return !rules || !rules.hidden.includes(def.name)
}

/** Obligatoire ? `required` statique tant que le champ est applicable, ou rendu obligatoire par une règle. */
export function isFieldRequired(def: FieldDefinition, rules: FieldRulesPreview | null): boolean {
  if (isComputedField(def)) return false
  if (rules?.hidden.includes(def.name)) return false
  return def.required || !!rules?.requiredByRule.includes(def.name)
}
