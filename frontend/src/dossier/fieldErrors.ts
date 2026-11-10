import type { FieldError } from '@/api/types'
import { i18n } from '@/i18n'

/**
 * Texte affiché sous un champ. Pour UNIQUE, seul le modèle traduit est utilisé : le message serveur
 * (« Value already used by dossier … ») n'est jamais recopié, il pourrait un jour contenir la valeur.
 * La référence d'un autre dossier n'apparaît que pour ce code.
 */
export function describeFieldError(error: FieldError): string {
  if (error.code === 'UNIQUE') {
    const reference = error.reference?.trim()
    if (reference) return i18n.global.t('dossier.fieldError.unique', { reference })
    return i18n.global.t('dossier.fieldError.uniqueUnknown')
  }
  if (error.code === 'MANAGED_BY_WORKFLOW') {
    return i18n.global.t('dossier.fieldError.managedByWorkflow')
  }
  return error.message.trim() || error.code
}

/** Erreur dont le champ n'est pas affiché : le libellé (ou le nom) précède le texte, sans recopier le message serveur d'un UNIQUE. */
export function describeDetachedFieldError(error: FieldError, label?: string): string {
  const shown = label?.trim() || error.field
  return i18n.global.t('dossier.fieldError.detached', { label: shown, message: describeFieldError(error) })
}
