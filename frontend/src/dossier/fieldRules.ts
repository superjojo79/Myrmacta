import type { FieldDefinition, FieldRulesPreview } from '@/api/types'

/** Vrai si au moins un champ du type porte une règle (calcul, visibilité, obligation conditionnelle). */
export function hasFieldRules(definitions: FieldDefinition[]): boolean {
  return definitions.some((d) => !!d.formula || !!d.visibleWhen || !!d.requiredWhen)
}

export function isComputedField(def: FieldDefinition): boolean {
  return !!def.formula
}

/** TODO §126 : champ géré par le workflow — jamais saisi, affiché en lecture seule (D-MANAGED-UI). */
export function isWorkflowManagedField(def: FieldDefinition): boolean {
  return !!def.managedByWorkflow
}

/** Champ affiché en `<output>` dans un formulaire éditable (calculé ou géré par le workflow). */
export function isReadOnlyOutputField(def: FieldDefinition): boolean {
  return isComputedField(def) || isWorkflowManagedField(def)
}

/** Champ applicable ? Sans aperçu des règles (pas encore reçu, type sans règle) : toujours oui. */
export function isFieldApplicable(def: FieldDefinition, rules: FieldRulesPreview | null): boolean {
  return !rules || !rules.hidden.includes(def.name)
}

/** Obligatoire ? `required` statique tant que le champ est applicable, ou rendu obligatoire par une règle. */
export function isFieldRequired(def: FieldDefinition, rules: FieldRulesPreview | null): boolean {
  if (isReadOnlyOutputField(def)) return false
  if (rules?.hidden.includes(def.name)) return false
  return def.required || !!rules?.requiredByRule.includes(def.name)
}
