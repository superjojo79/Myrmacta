import { createBeidConnectBridge } from './beidConnect'
import { createWebEidBridge } from './webEid'
import { CardBridgeError, type CardBridge, type CardBridgeId } from './types'

export * from './types'
export { createBeidConnectBridge } from './beidConnect'
export { createWebEidBridge } from './webEid'

export type BridgeFactories = Record<CardBridgeId, (lang: string) => CardBridge>

const DEFAULT_FACTORIES: BridgeFactories = {
  'web-eid': (lang) => createWebEidBridge(lang),
  beidconnect: (lang) => createBeidConnectBridge(lang),
}

export interface BridgeDetection {
  /** Premier pont autorisé qui répond ; `null` si aucun. */
  bridge: CardBridge | null
  /** Pourquoi chaque pont essayé a été écarté, dans l'ordre d'essai. */
  errors: CardBridgeError[]
}

/**
 * Essaie les ponts de `allowed` dans l'ordre (celui du serveur) et rend le premier disponible. Un identifiant inconnu est
 * ignoré : le serveur ne peut pas faire essayer à la page un pont qu'elle ne sait pas piloter.
 */
export async function detectCardBridge(
  allowed: readonly string[],
  lang: string,
  factories: BridgeFactories = DEFAULT_FACTORIES,
): Promise<BridgeDetection> {
  const errors: CardBridgeError[] = []
  for (const id of allowed) {
    if (!Object.prototype.hasOwnProperty.call(factories, id)) continue
    const bridge = factories[id as CardBridgeId](lang)
    try {
      await bridge.detect()
      return { bridge, errors }
    } catch (e) {
      errors.push(e instanceof CardBridgeError ? e : new CardBridgeError('failed'))
    }
  }
  return { bridge: null, errors }
}

/** L'erreur la plus utile à montrer quand aucun pont ne répond : contexte non sécurisé, puis mise à jour, puis absence. */
export function mostRelevantError(errors: readonly CardBridgeError[]): CardBridgeError {
  return (
    errors.find((e) => e.kind === 'insecure-context') ??
    errors.find((e) => e.kind === 'update-required') ??
    new CardBridgeError('unavailable')
  )
}
