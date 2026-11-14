import { computed, onBeforeUnmount, ref } from 'vue'
import { CardBridgeError, detectCardBridge, mostRelevantError, type BridgeFactories } from './index'
import type { CardCompleteRequest, CardPrepareRequest, CardPrepareResponse } from './types'

// Issue #10, lot 3b — enchaînement de la signature dans la page, commun au portail et à « Mes signatures » :
// détection du pont → certificat → prepare → (PIN) → signature par le pont → complete.

export type CardPhase = 'idle' | 'detecting' | 'reading' | 'preparing' | 'pin' | 'signing' | 'completing'

export const PIN_MIN_LENGTH = 4
export const PIN_MAX_LENGTH = 12

export interface CardSignatureOptions {
  /** Ponts autorisés par le serveur, dans l'ordre à essayer. */
  bridges: () => readonly string[]
  /** Langue de l'interface (le pont n'en retient que deux lettres). */
  lang: () => string
  prepare: (request: CardPrepareRequest) => Promise<CardPrepareResponse>
  complete: (request: CardCompleteRequest) => Promise<unknown>
  onSigned: () => void | Promise<void>
  /** Toute erreur de l'enchaînement : `CardBridgeError` du pont, ou erreur de l'API à interpréter par la vue. */
  onError: (e: unknown) => void | Promise<void>
  /** Aucun pont ne répond : l'erreur la plus utile à montrer. */
  onNoBridge: (e: CardBridgeError) => void
  factories?: BridgeFactories
}

export function useCardSignature(options: CardSignatureOptions) {
  const phase = ref<CardPhase>('idle')
  /**
   * Saisie du PIN (D-EID-PIN) : seulement liée au champ, vidée dès la validation ou l'abandon. Jamais dans Pinia, le
   * stockage du navigateur, l'URL, un journal, un message d'erreur ni une requête vers le serveur.
   */
  const pin = ref('')
  const pinValid = computed(() => pin.value.length >= PIN_MIN_LENGTH && pin.value.length <= PIN_MAX_LENGTH)
  const active = computed(() => phase.value !== 'idle')
  let pinResolver: ((value: string | null) => void) | null = null

  function askPin(): Promise<string | null> {
    pin.value = ''
    return new Promise((resolve) => {
      pinResolver = resolve
    })
  }

  /** Remet le PIN au pont : la variable de la page est vidée avant même l'appel de signature. */
  function submitPin() {
    if (!pinResolver || !pinValid.value) return
    const value = pin.value
    pin.value = ''
    const resolve = pinResolver
    pinResolver = null
    resolve(value)
  }

  function cancelPin() {
    pin.value = ''
    const resolve = pinResolver
    pinResolver = null
    resolve?.(null)
  }

  async function start() {
    if (phase.value !== 'idle') return
    try {
      phase.value = 'detecting'
      const lang = options.lang()
      const { bridge, errors } = await detectCardBridge(options.bridges(), lang, options.factories)
      if (!bridge) {
        options.onNoBridge(mostRelevantError(errors))
        return
      }
      phase.value = 'reading'
      const certificate = await bridge.readCertificate()
      phase.value = 'preparing'
      const prepared = await options.prepare({
        certificate: certificate.certificate,
        certificateChain: certificate.certificateChain,
      })
      if (prepared.hashFunction !== 'SHA-256' && prepared.hashFunction !== 'SHA-384') throw new CardBridgeError('failed')
      let secret: string | null = null
      if (bridge.needsPin) {
        phase.value = 'pin'
        secret = await askPin()
        if (secret === null) throw new CardBridgeError('cancelled')
      }
      phase.value = 'signing'
      let signature: string
      try {
        signature = await bridge.sign(certificate, prepared.digest, prepared.hashFunction, secret)
      } finally {
        secret = null
      }
      phase.value = 'completing'
      await options.complete({
        certificate: certificate.certificate,
        certificateChain: certificate.certificateChain,
        signingTime: prepared.signingTime,
        documentDigest: prepared.documentDigest,
        signature,
      })
      await options.onSigned()
    } catch (e) {
      await options.onError(e)
    } finally {
      pin.value = ''
      pinResolver = null
      phase.value = 'idle'
    }
  }

  onBeforeUnmount(cancelPin)

  return { phase, pin, pinValid, active, start, submitPin, cancelPin }
}

/** Clé et paramètre du message d'une erreur du pont, sous `prefix` (`….card`). */
export function cardErrorMessage(prefix: string, e: CardBridgeError): { key: string; named?: { count: number } } {
  switch (e.kind) {
    case 'update-required':
      return { key: `${prefix}.errors.updateRequired` }
    case 'no-reader':
      return { key: `${prefix}.errors.noReader` }
    case 'no-card':
      return { key: `${prefix}.errors.noCard` }
    case 'card-blocked':
      return { key: `${prefix}.errors.cardBlocked` }
    case 'insecure-context':
      return { key: `${prefix}.errors.insecureContext` }
    case 'pin-incorrect':
      return e.attemptsLeft !== undefined
        ? { key: `${prefix}.errors.pinIncorrectLeft`, named: { count: e.attemptsLeft } }
        : { key: `${prefix}.errors.pinIncorrect` }
    case 'cancelled':
    case 'timeout':
    case 'failed':
      return { key: `${prefix}.errors.${e.kind}` }
    default:
      return { key: `${prefix}.errors.unavailable` }
  }
}

/** Aucun pont ne répond : « aucun logiciel détecté », ou l'erreur plus précise (mise à jour, contexte non sécurisé). */
export function noBridgeMessage(prefix: string, e: CardBridgeError): { key: string; named?: { count: number } } {
  return e.kind === 'unavailable' ? { key: `${prefix}.noBridge` } : cardErrorMessage(prefix, e)
}
