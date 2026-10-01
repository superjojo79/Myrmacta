import type { Messages } from './fr'
import common from './en/common'
import pilotage from './en/pilotage'
import dossier from './en/dossier'
import portal from './en/portal'
import shell from './en/shell'

const en: Messages = { common, portal, shell, dossier, ...pilotage }

export default en
