/**
 * TODO §93 — variables de démarrage saisies dans « Démarrer un workflow ». Le serveur reste l'autorité
 * (`WorkflowVariables.assertNoReservedKeys`) ; ce contrôle évite seulement un aller-retour en erreur.
 */
export interface StartVariableRow {
  name: string
  value: string
}

/** TODO §111 — variable déclarée dans le BPMN déployé, lue par le dialogue. */
export interface DeclaredStartVariable {
  name: string
  label: string
  required: boolean
}

export type StartVariableError = 'invalid' | 'reserved' | 'duplicate'

/** Mêmes noms que `WorkflowVariables.RESERVED` côté serveur. */
export const RESERVED_START_VARIABLES = [
  'dossierId',
  'dossierWorkflowLinkId',
  'tenantId',
  'dossierFields',
  'connectorResult',
  'generatedDocumentId',
]

const NAME = /^[A-Za-z_][A-Za-z0-9_]*$/

/** Nom rogné, valeur envoyée telle quelle (texte) ; une ligne entièrement vide est ignorée. */
export function validateStartVariables(rows: StartVariableRow[]): {
  variables: Record<string, string>
  errors: Record<number, StartVariableError>
} {
  const variables: Record<string, string> = {}
  const errors: Record<number, StartVariableError> = {}
  rows.forEach((row, index) => {
    const name = row.name.trim()
    if (!name && !row.value) return
    if (!NAME.test(name)) {
      errors[index] = 'invalid'
    } else if (RESERVED_START_VARIABLES.includes(name)) {
      errors[index] = 'reserved'
    } else if (Object.prototype.hasOwnProperty.call(variables, name)) {
      errors[index] = 'duplicate'
    } else {
      variables[name] = row.value
    }
  })
  return { variables, errors }
}

/** Valeurs du formulaire déclaré. Une facultative blanche n'est pas envoyée. */
export function collectDeclaredStartVariables(
  declarations: DeclaredStartVariable[],
  values: Record<string, string>,
): { variables: Record<string, string>; missing: DeclaredStartVariable[] } {
  const missing = declarations.filter((declaration) => declaration.required && !(values[declaration.name] ?? '').trim())
  const variables: Record<string, string> = {}
  if (missing.length === 0) {
    for (const declaration of declarations) {
      const value = (values[declaration.name] ?? '').trim()
      if (value) variables[declaration.name] = value
    }
  }
  return { variables, missing }
}
