import enCommon from './en/common'
import enDossier from './en/dossier'
import enPortal from './en/portal'
import frCommon from './fr/common'
import frDossier from './fr/dossier'
import frPortal from './fr/portal'

/** Page publique : seuls les textes du portail et les communs sont embarqués (aucun libellé d'administration n'y est servi) ; `dossier` pour la saisie des champs (DynamicFieldInput). */
export const messages = {
  fr: { common: frCommon, portal: frPortal, dossier: frDossier },
  en: { common: enCommon, portal: enPortal, dossier: enDossier },
}
