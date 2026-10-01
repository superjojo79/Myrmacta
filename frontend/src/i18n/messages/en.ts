import type { Messages } from './fr'
import common from './en/common'
import pilotage from './en/pilotage'
import portal from './en/portal'

const en: Messages = { common, portal, ...pilotage }

export default en
