import { readSigners, readValue, type Moddled } from './blockValues'
import type { BlockParameter, BuildingBlock, ValueOption } from './types'

// TODO §73 — diagnostics de brique, TOUJOURS non bloquants (S-DIAG-4) : ils guident la saisie, seul le déploiement
// refuse (D-PALETTE-DEPLOY-*). `options` renvoie la liste résolue d'une source, `null` si non listable / indisponible
// et `undefined` si pas encore chargée : dans ces deux cas aucune « référence absente » n'est affirmée (S-DIAG-5).

export type DiagnosticKind = 'required' | 'literal' | 'reference' | 'companion' | 'email' | 'invalid' | 'recursion'

export interface Diagnostic {
  kind: DiagnosticKind
  /** Paramètre concerné, absent pour un diagnostic de brique (compagnon manquant). */
  param?: string
  message: string
}

export interface DiagnosticContext {
  options(source: string): ValueOption[] | null | undefined
  /** Le process contient une attente du message `signatureCompleted`. */
  hasSignatureCompletedCatch: boolean
  /** Clé du process ouvert (détection d'un dossier enfant qui redémarre le même process). */
  processKey?: string | null
}

const EXPRESSION = /[$#]\{/

function isEmpty(bo: Moddled, block: BuildingBlock, param: BlockParameter): boolean {
  if (param.type === 'SIGNERS') return readSigners(bo).length === 0
  return readValue(bo, block, param).trim() === ''
}

export function diagnoseParameter(
  bo: Moddled,
  block: BuildingBlock,
  param: BlockParameter,
  ctx: DiagnosticContext,
): Diagnostic[] {
  const out: Diagnostic[] = []
  if (param.type === 'SIGNERS') {
    if (param.required && isEmpty(bo, block, param)) {
      out.push({ kind: 'required', param: param.name, message: 'Au moins un signataire est requis.' })
    }
    return out
  }
  const raw = readValue(bo, block, param)
  const value = raw.trim()
  if (value === '') {
    if (param.required) out.push({ kind: 'required', param: param.name, message: `« ${param.label} » est obligatoire.` })
    return out
  }
  if (EXPRESSION.test(value)) {
    out.push({
      kind: 'literal',
      param: param.name,
      message: 'Valeur littérale attendue : les expressions ${…} sont refusées au déploiement.',
    })
    return out
  }
  // Revue I4 : mêmes règles de forme que le déploiement (BlockAttributeRules), signalées avant d'y arriver.
  const bad = formatProblem(param, raw)
  if (bad) {
    out.push({ kind: 'invalid', param: param.name, message: bad })
    return out
  }
  if (param.type === 'REFERENCE' && param.valueSource) {
    const options = ctx.options(param.valueSource)
    if (options && !options.some((o) => o.value === value)) {
      out.push({ kind: 'reference', param: param.name, message: `« ${value} » n'existe plus dans la liste disponible.` })
    }
  }
  if (param.name === 'notificationTo' && value.includes('@')) {
    out.push({
      kind: 'email',
      param: param.name,
      message: 'Adresse e-mail littérale dans le BPMN : pour une personne physique, préférer une variable ou un identifiant utilisateur.',
    })
  }
  return out
}

const UUID_SOURCES = new Set(['connectorInstances', 'notificationWebhooks'])
const UUID_FORM = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const KEY_FORM = /^[A-Za-z0-9_.:-]+$/

function formatProblem(param: BlockParameter, raw: string): string | undefined {
  const value = raw.trim()
  if (param.name !== 'notificationBody' && (raw !== value || /[\r\n\t]/.test(raw))) {
    return 'Espaces de bord ou retours à la ligne interdits : le déploiement refusera cette valeur.'
  }
  if (param.type === 'ENUM' && !param.enumValues.includes(value)) {
    return `« ${value} » n'est pas une valeur valide (${param.enumValues.join(', ')}).`
  }
  if (param.type === 'ENUM_MULTI') {
    const unknown = value.split(',').map((v) => v.trim()).filter((v) => v && !param.enumValues.includes(v))
    if (unknown.length > 0) return `Valeur(s) invalide(s) : ${unknown.join(', ')} (${param.enumValues.join(', ')}).`
  }
  if (param.type === 'REFERENCE' && param.binding === 'ATTRIBUTE') {
    if (UUID_SOURCES.has(param.valueSource ?? '') ? !UUID_FORM.test(value) : !KEY_FORM.test(value)) {
      return UUID_SOURCES.has(param.valueSource ?? '') ? 'Un identifiant (UUID) est attendu.' : 'Format de clé invalide.'
    }
  }
  return undefined
}

export function diagnoseBlock(bo: Moddled, block: BuildingBlock, ctx: DiagnosticContext): Diagnostic[] {
  const out = block.parameters.flatMap((param) => diagnoseParameter(bo, block, param, ctx))
  if (block.id === 'create-child-dossier' && ctx.processKey) {
    const target = block.parameters.find((p) => p.name === 'workflowProcessKey')
    if (target && readValue(bo, block, target).trim() === ctx.processKey) {
      out.push({
        kind: 'recursion',
        param: 'workflowProcessKey',
        message: 'Ce dossier enfant redémarre le même process : sans condition d\'arrêt, la chaîne de dossiers est infinie.',
      })
    }
  }
  if (block.id === 'request-signature' && !ctx.hasSignatureCompletedCatch) {
    out.push({
      kind: 'companion',
      message: 'Ajoutez en aval un événement intermédiaire de réception du message « signatureCompleted », sinon le process ne reprend pas.',
    })
  }
  return out
}

/** Texte d'aide des paramètres ATTRIBUTE : renseignée, la valeur prime sur une variable de process de même nom (S-DIAG-8, revue I1). */
export function priorityHint(param: BlockParameter): string | undefined {
  if (param.binding !== 'ATTRIBUTE' || param.type === 'SIGNERS') return undefined
  return `Renseignée, cette valeur prime sur une variable de process « ${param.name} » : elle n'est pas modifiable à l'exécution.`
}
