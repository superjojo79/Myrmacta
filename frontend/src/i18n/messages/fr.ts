import common from './fr/common'
import pilotage from './fr/pilotage'
import dossier from './fr/dossier'
import portal from './fr/portal'
import shell from './fr/shell'

/** Langue d'origine et de repli : tout texte de l'interface naît ici ; l'anglais est typé sur cette forme (une clé oubliée ne compile pas). */
const fr = { common, portal, shell, dossier, ...pilotage }

export default fr
export type Messages = typeof fr
