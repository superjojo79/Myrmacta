import { summarizeCertificate } from './x509'
import { CardBridgeError, type CardBridge, type CardBridgeErrorKind, type CardCertificate, type CardChoice, type CardHashFunction } from './types'

// BeIDConnect 2.12 : extension du navigateur qui expose `window.EIDChromeExt`. Méthodes à promesses ; un échec rejette
// avec un objet dont `message` (et `result`) vaut le code. L'extension ne gère aucun délai : ceux-ci sont imposés ici.

/** Constante exigée par l'interface de l'extension, qui l'ignore. */
export const BEID_MAC = '0123456789ABCDEF0123456789ABCDEF'
/** Délais de la référence fédérale. */
export const BEID_TIMEOUT_MS = 20_000
export const BEID_PINPAD_SIGN_TIMEOUT_MS = 60_000

interface Extension {
  getVersion(): Promise<{ result?: string; version?: string }>
  getUserCertificates(language: string, mac: string, filter: string): Promise<{
    result?: string
    Readers?: { ReaderName?: string; ReaderType?: string; cardType?: string; certificates?: string[] }[]
  }>
  getUserCertificateChain(language: string, mac: string, certificate: string): Promise<{
    result?: string
    certificateChain?: { rootCA?: string; subCA?: string[] }
  }>
  sign(language: string, mac: string, certificate: string, algo: string, digest: string, pin: string | null): Promise<{
    result?: string
    signature?: string
  }>
}

type ExtensionConstructor = new () => Extension

function kindOf(code: string): { kind: CardBridgeErrorKind; attemptsLeft?: number } {
  switch (code) {
    case 'no_reader':
      return { kind: 'no-reader' }
    case 'no_card':
      return { kind: 'no-card' }
    case 'pin_incorrect':
    case 'pin_too_short':
    case 'pin_too_long':
    case 'pin_length':
      return { kind: 'pin-incorrect' }
    case 'pin_3_attempts_left':
      return { kind: 'pin-incorrect', attemptsLeft: 3 }
    case 'pin_2_attempts_left':
      return { kind: 'pin-incorrect', attemptsLeft: 2 }
    case 'pin_1_attempt_left':
      return { kind: 'pin-incorrect', attemptsLeft: 1 }
    case 'card_blocked':
      return { kind: 'card-blocked' }
    case 'pin_timeout':
      return { kind: 'timeout' }
    case 'cancel':
      return { kind: 'cancelled' }
    case 'timeout':
      // « timeout » est le code de l'application native absente.
      return { kind: 'unavailable' }
    default:
      // busy, general_error, card_error, unsupported_reader, signature_failed, technical_error et tout code inconnu.
      return { kind: 'failed' }
  }
}

/** Normalise un rejet de l'extension : jamais de texte brut dans l'erreur, seulement le code dans `bridgeCode`. */
function toBridgeError(e: unknown): CardBridgeError {
  if (e instanceof CardBridgeError) return e
  const raw = e as { message?: unknown; result?: unknown } | null
  const code = typeof raw?.message === 'string' && raw.message ? raw.message : typeof raw?.result === 'string' ? raw.result : ''
  const { kind, attemptsLeft } = kindOf(code)
  return new CardBridgeError(kind, { attemptsLeft, bridgeCode: code || undefined })
}

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new CardBridgeError('timeout')), ms)
    promise.then(
      (value) => {
        clearTimeout(timer)
        resolve(value)
      },
      (e) => {
        clearTimeout(timer)
        reject(toBridgeError(e))
      },
    )
  })
}

/** Réponse inattendue : `result` autre que `OK` se lit comme un code d'erreur. */
function ensureOk(response: { result?: string } | null | undefined): void {
  if (!response || typeof response !== 'object') throw new CardBridgeError('failed')
  if (response.result !== undefined && response.result !== 'OK') throw toBridgeError({ result: response.result })
}

/** `SHA-256` → `SHA256` ; une fonction que le pont ne connaît pas fait échouer proprement. */
function algorithmOf(hashFunction: CardHashFunction): string {
  switch (hashFunction) {
    case 'SHA-256':
      return 'SHA256'
    case 'SHA-384':
      return 'SHA384'
    default:
      throw new CardBridgeError('failed')
  }
}

export function isBeidConnectPresent(win: Window = window): boolean {
  const ctor = (win as unknown as { EIDChromeExt?: unknown }).EIDChromeExt
  return 'EIDChromeExt' in win && typeof ctor === 'function'
}

/** `lang` : code de langue à deux lettres. */
export function createBeidConnectBridge(lang: string, win: Window = window): CardBridge {
  const language = lang.slice(0, 2).toLowerCase()
  let extension: Extension | null = null

  function ext(): Extension {
    if (!isBeidConnectPresent(win)) throw new CardBridgeError('unavailable')
    extension ??= new (win as unknown as { EIDChromeExt: ExtensionConstructor }).EIDChromeExt()
    return extension
  }

  return {
    id: 'beidconnect',
    async detect() {
      const response = await withTimeout(Promise.resolve().then(() => ext().getVersion()), BEID_TIMEOUT_MS)
      ensureOk(response)
    },
    /**
     * Un choix par couple lecteur × certificat, filtré sur les certificats de signature (`NONREPUDIATION`). Un certificat
     * dont l'usage de clé est lisible et sans non-répudiation est écarté ; si cela ne laisse rien, tout est gardé et le
     * serveur tranche (`CERTIFICATE_NOT_QUALIFIED`).
     */
    async listChoices(): Promise<CardChoice[]> {
      const found = await withTimeout(Promise.resolve().then(() => ext().getUserCertificates(language, BEID_MAC, 'NONREPUDIATION')), BEID_TIMEOUT_MS)
      ensureOk(found)
      const readers = found.Readers ?? []
      if (readers.length === 0) throw new CardBridgeError('no-reader')
      const all: { choice: CardChoice; signing: boolean }[] = []
      readers.forEach((reader, readerIndex) => {
        ;(reader.certificates ?? []).forEach((certificate, certificateIndex) => {
          if (typeof certificate !== 'string' || !certificate) return
          const summary = summarizeCertificate(certificate)
          all.push({
            signing: summary?.nonRepudiation !== false,
            choice: {
              id: `${readerIndex}:${certificateIndex}`,
              certificate: { certificate, certificateChain: [] },
              readerName: typeof reader.ReaderName === 'string' && reader.ReaderName ? reader.ReaderName : null,
              holder: summary?.holder ?? null,
              notAfter: summary?.notAfter ?? null,
              // Type de lecteur inconnu : le PIN est demandé (pas de signature « à l'aveugle » sur un clavier qui n'existe pas).
              needsPin: reader.ReaderType !== 'pinpad',
            },
          })
        })
      })
      if (all.length === 0) throw new CardBridgeError('no-card')
      const signing = all.filter((c) => c.signing)
      return (signing.length > 0 ? signing : all).map((c) => c.choice)
    },
    async certificateOf(choice: CardChoice): Promise<CardCertificate> {
      const chain = await withTimeout(
        Promise.resolve().then(() => ext().getUserCertificateChain(language, BEID_MAC, choice.certificate.certificate)),
        BEID_TIMEOUT_MS,
      )
      ensureOk(chain)
      const { rootCA, subCA } = chain.certificateChain ?? {}
      // Intermédiaires d'abord, racine en dernier.
      const certificateChain = [...(subCA ?? []), ...(rootCA ? [rootCA] : [])].filter((c) => typeof c === 'string' && c)
      return { certificate: choice.certificate.certificate, certificateChain }
    },
    async sign(choice, digest, hashFunction, pin) {
      const algo = algorithmOf(hashFunction)
      const pinpad = !choice.needsPin
      if (!pinpad && pin === null) throw new CardBridgeError('failed')
      const response = await withTimeout(
        Promise.resolve().then(() => ext().sign(language, BEID_MAC, choice.certificate.certificate, algo, digest, pinpad ? null : pin)),
        pinpad ? BEID_PINPAD_SIGN_TIMEOUT_MS : BEID_TIMEOUT_MS,
      )
      ensureOk(response)
      if (typeof response.signature !== 'string' || !response.signature) throw new CardBridgeError('failed')
      return response.signature
    },
  }
}
