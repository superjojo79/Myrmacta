import { summarizeCertificate } from './x509'
import { CardBridgeError, type CardBridge, type CardBridgeErrorKind, type CardCertificate, type CardChoice, type CardHashFunction } from './types'

// Web eID, protocole de web-eid.js 2.1.0 (bibliothèque de référence, non publiée sur npmjs) réimplémenté sans dépendance :
// `window.postMessage` vers l'extension du navigateur, réponses `web-eid:<action>-ack|success|failure`.
// Le PIN est saisi dans la fenêtre du logiciel eID, jamais dans la page.

export const WEB_EID_LIBRARY_VERSION = '2.1.0'
/** Délais de la bibliothèque de référence. */
export const WEB_EID_ACK_TIMEOUT_MS = 1000
export const WEB_EID_NATIVE_TIMEOUT_MS = 5000
export const WEB_EID_USER_TIMEOUT_MS = 120_000

type Action = 'status' | 'get-signing-certificate' | 'sign'

interface Message {
  action?: unknown
  error?: { code?: unknown } | null
  [key: string]: unknown
}

/** Canal vers l'extension : isolé pour les tests. */
export interface WebEidTransport {
  send(message: Record<string, unknown>): void
  /** Écoute les messages de l'extension ; rend la fonction de désinscription. */
  listen(handler: (data: Message) => void): () => void
  isSecureContext(): boolean
}

export function windowTransport(win: Window = window): WebEidTransport {
  return {
    send: (message) => win.postMessage(message, '*'),
    listen(handler) {
      const listener = (event: MessageEvent) => {
        // Seule la page elle-même (l'extension injecte dans la page) est crue ; son propre envoi repasse ici et est écarté
        // par le nom d'action.
        if (event.source !== win) return
        const data = event.data as Message | null
        if (data && typeof data === 'object' && typeof data.action === 'string' && data.action.startsWith('web-eid:')) {
          handler(data)
        }
      }
      win.addEventListener('message', listener)
      return () => win.removeEventListener('message', listener)
    },
    isSecureContext: () => win.isSecureContext === true,
  }
}

function errorKind(code: unknown): CardBridgeErrorKind {
  switch (code) {
    case 'ERR_WEBEID_EXTENSION_UNAVAILABLE':
    case 'ERR_WEBEID_NATIVE_UNAVAILABLE':
      return 'unavailable'
    case 'ERR_WEBEID_VERSION_MISMATCH':
    case 'ERR_WEBEID_VERSION_INVALID':
      return 'update-required'
    case 'ERR_WEBEID_USER_CANCELLED':
      return 'cancelled'
    case 'ERR_WEBEID_USER_TIMEOUT':
    case 'ERR_WEBEID_ACTION_TIMEOUT':
      return 'timeout'
    case 'ERR_WEBEID_CONTEXT_INSECURE':
      return 'insecure-context'
    default:
      // NATIVE_INVALID_ARGUMENT, NATIVE_FATAL, ACTION_PENDING, MISSING_PARAMETER, UNKNOWN_ERROR et tout code inconnu.
      return 'failed'
  }
}

/** `lang` : code ISO 639-1 à deux lettres. */
export function createWebEidBridge(lang: string, transport: WebEidTransport = windowTransport()): CardBridge {
  const options = { lang: lang.slice(0, 2).toLowerCase() }
  const pending = new Set<Action>()

  function call(action: Action, payload: Record<string, unknown>, actionTimeoutMs: number): Promise<Message> {
    if (pending.has(action)) {
      return Promise.reject(new CardBridgeError('failed', { bridgeCode: 'ERR_WEBEID_ACTION_PENDING' }))
    }
    pending.add(action)
    const prefix = `web-eid:${action}`
    return new Promise<Message>((resolve, reject) => {
      let timer: ReturnType<typeof setTimeout>
      const finish = () => {
        clearTimeout(timer)
        stop()
        pending.delete(action)
      }
      // Pas d'accusé de réception dans la seconde : l'extension est absente.
      timer = setTimeout(() => {
        finish()
        reject(new CardBridgeError('unavailable', { bridgeCode: 'ERR_WEBEID_EXTENSION_UNAVAILABLE' }))
      }, WEB_EID_ACK_TIMEOUT_MS)
      const stop = transport.listen((data) => {
        if (data.action === `${prefix}-ack`) {
          clearTimeout(timer)
          timer = setTimeout(() => {
            finish()
            // Pour `status`, ne pas obtenir de réponse de l'application native veut dire qu'elle est absente.
            reject(new CardBridgeError(action === 'status' ? 'unavailable' : 'timeout', { bridgeCode: 'ERR_WEBEID_ACTION_TIMEOUT' }))
          }, actionTimeoutMs)
        } else if (data.action === `${prefix}-success`) {
          finish()
          resolve(data)
        } else if (data.action === `${prefix}-failure`) {
          finish()
          const code = data.error?.code
          reject(new CardBridgeError(errorKind(code), { bridgeCode: typeof code === 'string' ? code : undefined }))
        }
      })
      transport.send({ action: prefix, libraryVersion: WEB_EID_LIBRARY_VERSION, ...payload })
    })
  }

  return {
    id: 'web-eid',
    async detect() {
      if (!transport.isSecureContext()) throw new CardBridgeError('insecure-context')
      await call('status', {}, WEB_EID_NATIVE_TIMEOUT_MS)
    },
    // Le logiciel natif de Web eID affiche lui-même le choix du lecteur et du certificat : un seul choix côté page.
    async listChoices(): Promise<CardChoice[]> {
      const response = await call('get-signing-certificate', { options }, WEB_EID_USER_TIMEOUT_MS)
      if (typeof response.certificate !== 'string' || !response.certificate) throw new CardBridgeError('failed')
      // Web eID ne rend que le certificat feuille : la chaîne est laissée au serveur.
      const summary = summarizeCertificate(response.certificate)
      return [
        {
          id: '0:0',
          certificate: { certificate: response.certificate, certificateChain: [] },
          readerName: null,
          holder: summary?.holder ?? null,
          notAfter: summary?.notAfter ?? null,
          needsPin: false,
        },
      ]
    },
    certificateOf: (choice: CardChoice): Promise<CardCertificate> => Promise.resolve(choice.certificate),
    async sign(choice: CardChoice, digest: string, hashFunction: CardHashFunction): Promise<string> {
      const response = await call(
        'sign',
        // `certificate` : exactement la chaîne rendue par get-signing-certificate.
        { certificate: choice.certificate.certificate, hash: digest, hashFunction, options },
        WEB_EID_USER_TIMEOUT_MS,
      )
      if (typeof response.signature !== 'string' || !response.signature) throw new CardBridgeError('failed')
      return response.signature
    },
  }
}
