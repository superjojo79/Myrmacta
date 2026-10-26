// TODO §73 (palette-metier-bpmn) — miroir du catalogue serveur `GET /api/bpmn-building-blocks`.

export type BlockCategory = 'DOSSIER' | 'DOCUMENT' | 'INTEGRATION' | 'DECISION' | 'HUMAN' | 'SIGNATURE'
export type BlockElement = 'SERVICE_TASK' | 'USER_TASK'
export type ParameterType =
  | 'TEXT'
  | 'ENUM'
  | 'ENUM_MULTI'
  | 'INTEGER'
  | 'REFERENCE'
  | 'SOURCE_REF'
  | 'SIGNERS'
export type ParameterBinding = 'ATTRIBUTE' | 'FIELD_ELEMENT' | 'FORM_KEY_SUFFIX' | 'SIGNER_ELEMENTS'

export interface BlockParameter {
  name: string
  label: string
  type: ParameterType
  binding: ParameterBinding
  required: boolean
  valueSource: string | null
  enumValues: string[]
  maxLength: number
  min: number | null
  max: number | null
  help: string | null
  /** Paramètre pouvant s'étendre sur plusieurs lignes (publié par le catalogue ; absent des anciens catalogues). */
  multiline?: boolean
}

export interface BlockMarker {
  delegate: string | null
  serviceTaskType: string | null
  formKeyPrefix: string | null
  portalRecipient: boolean
  /** TODO §106 — créer l'élément avec flowable:async="true" (absent des anciens catalogues). */
  async?: boolean
}

export interface BuildingBlock {
  id: string
  label: string
  description: string
  category: BlockCategory
  element: BlockElement
  marker: BlockMarker
  parameters: BlockParameter[]
}

export interface BuildingBlockCatalog {
  version: number
  blocks: BuildingBlock[]
}

export interface ValueOption {
  value: string
  label: string
}
