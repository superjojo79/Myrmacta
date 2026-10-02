import type { Messages } from './fr'
import admin from './en/admin'
import common from './en/common'
import pilotage from './en/pilotage'
import dossier from './en/dossier'
import portal from './en/portal'
import shell from './en/shell'
import workflow from './en/workflow'

const en: Messages = { common, portal, shell, dossier, workflow, admin, ...pilotage }

export default en
