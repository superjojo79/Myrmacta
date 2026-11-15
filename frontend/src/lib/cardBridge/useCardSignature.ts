import { computed, onBeforeUnmount, ref } from 'vue'
import { CardBridgeError, detectCardBridge, mostRelevantError, type BridgeFactories } from './index'
import type { CardBridgeId, CardChoice, CardCompleteRequest, CardPrepareRequest, CardPrepareResponse } from './types'

// Issue #10, lot 3b — enchaînement de la signature dans la page, commun au portail et à « Mes signatures » :
// détection du pont → certificat → prepare → (PIN) → signature par le pont → complete.

export type CardPhase = 'idle' | 'detecting' | 'reading' | 'choosing' | 'preparing' | 'pin' | 'signing' | 'completing'

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

  // Lot 3c — étape de choix du lecteur et du certificat, quand le pont offre plus d'un choix.
  const bridgeId = ref<CardBridgeId | null>(null)
  const choices = ref<CardChoice[]>([])
  const selectedChoice = ref('')
  let choiceResolver: ((id: string | null) => void) | null = null

  function askChoice(list: CardChoice[]): Promise<string | null> {
    choices.value = list
    selectedChoice.value = list[0]!.id
    return new Promise((resolve) => {
      choiceResolver = resolve
    })
  }

  function submitChoice() {
    const resolve = choiceResolver
    choiceResolver = null
    resolve?.(selectedChoice.value)
  }

  function cancelChoice() {
    const resolve = choiceResolver
    choiceResolver = null
    resolve?.(null)
  }

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
      bridgeId.value = bridge.id
      phase.value = 'reading'
      const offered = await bridge.listChoices()
      const first = offered[0]
      if (!first) throw new CardBridgeError('failed')
      let choice: CardChoice = first
      if (offered.length > 1) {
        phase.value = 'choosing'
        const picked = await askChoice(offered)
        choices.value = []
        if (picked === null) throw new CardBridgeError('cancelled')
        const found = offered.find((c) => c.id === picked)
        if (!found) throw new CardBridgeError('failed')
        choice = found
      }
      phase.value = 'preparing'
      const certificate = await bridge.certificateOf(choice)
      const prepared = await options.prepare({
        certificate: certificate.certificate,
        certificateChain: certificate.certificateChain,
      })
      if (prepared.hashFunction !== 'SHA-256' && prepared.hashFunction !== 'SHA-384') throw new CardBridgeError('failed')
      let secret: string | null = null
      if (choice.needsPin) {
        phase.value = 'pin'
        secret = await askPin()
        if (secret === null) throw new CardBridgeError('cancelled')
      }
      phase.value = 'signing'
      let signature: string
      try {
        signature = await bridge.sign(choice, prepared.digest, prepared.hashFunction, secret)
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
      choiceResolver = null
      choices.value = []
      phase.value = 'idle'
    }
  }

  onBeforeUnmount(() => {
    cancelPin()
    cancelChoice()
  })

  return { phase, pin, pinValid, active, bridgeId, choices, selectedChoice, start, submitPin, cancelPin, submitChoice, cancelChoice }
}

/** Clé et paramètre du message d'une erreur du pont, sous `prefix` (`….card`). */
export function cardErrorMessage(prefix: string, e: CardBridgeError, alone = false): { key: string; named?: { count: number } } {
  // `alone` : le téléversement n'est pas offert, le message ne renvoie pas à « l'autre moyen ».
  const suffix = alone ? 'Alone' : ''
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
      return { key: `${prefix}.errors.${e.kind}` }
    case 'failed':
      return { key: `${prefix}.errors.failed${suffix}` }
    default:
      return { key: `${prefix}.errors.unavailable${suffix}` }
  }
}

/** Aucun pont ne répond : « aucun logiciel détecté », ou l'erreur plus précise (mise à jour, contexte non sécurisé). */
export function noBridgeMessage(prefix: string, e: CardBridgeError, alone = false): { key: string; named?: { count: number } } {
  return e.kind === 'unavailable' ? { key: `${prefix}.noBridge${alone ? 'Alone' : ''}` } : cardErrorMessage(prefix, e, alone)
}
