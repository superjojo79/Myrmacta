import common from './fr/common'
import pilotage from './fr/pilotage'
import portal from './fr/portal'

/** Langue d'origine et de repli : tout texte de l'interface naît ici ; l'anglais est typé sur cette forme (une clé oubliée ne compile pas). */
const fr = { common, portal, ...pilotage }

export default fr
export type Messages = typeof fr
