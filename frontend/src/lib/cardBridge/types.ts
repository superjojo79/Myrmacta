// Issue #10, lot 3b — signature eID dans la page par un pont installé sur le poste (D-EID-BRIDGE).
// Types communs aux deux ponts (Web eID, BeIDConnect), à la façade et aux deux vues.

/** Identifiants des ponts, tels que le serveur les liste dans `cardBridges`. */
export type CardBridgeId = 'web-eid' | 'beidconnect'

/** Fonctions de condensé que le serveur peut demander (réponse de `prepare`). */
export type CardHashFunction = 'SHA-256' | 'SHA-384'

/** Certificat de signature de la carte (DER en base64) et, quand le pont la donne, sa chaîne. */
export interface CardCertificate {
  certificate: string
  certificateChain: string[]
}

/** Catégories d'erreur normalisées : les vues n'ont à connaître ni les codes de Web eID ni ceux de BeIDConnect. */
export type CardBridgeErrorKind =
  | 'unavailable'
  | 'update-required'
  | 'no-reader'
  | 'no-card'
  | 'cancelled'
  | 'timeout'
  | 'pin-incorrect'
  | 'card-blocked'
  | 'insecure-context'
  | 'failed'

export class CardBridgeError extends Error {
  readonly kind: CardBridgeErrorKind
  /** Essais restants avant le blocage de la carte, quand le pont les donne. */
  readonly attemptsLeft?: number
  /** Code brut du pont, pour le diagnostic : jamais affiché ni envoyé. */
  readonly bridgeCode?: string

  constructor(kind: CardBridgeErrorKind, options: { attemptsLeft?: number; bridgeCode?: string } = {}) {
    super(kind)
    this.kind = kind
    this.attemptsLeft = options.attemptsLeft
    this.bridgeCode = options.bridgeCode
  }
}

export interface CardBridge {
  readonly id: CardBridgeId
  /**
   * Vrai quand la page doit demander le PIN (BeIDConnect sur un lecteur sans clavier). Connu après `readCertificate` ;
   * avec Web eID le PIN est toujours saisi dans la fenêtre du logiciel eID.
   */
  readonly needsPin: boolean
  /** Vérifie que le pont répond ; rejette avec une `CardBridgeError` (indisponible, à mettre à jour, contexte non sécurisé). */
  detect(): Promise<void>
  readCertificate(): Promise<CardCertificate>
  /** `pin` est `null` quand `needsPin` est faux ; le PIN n'est utilisé que pour cet appel. Rend la signature en base64. */
  sign(certificate: CardCertificate, digest: string, hashFunction: CardHashFunction, pin: string | null): Promise<string>
}

// Échanges avec le serveur (identiques pour le portail et « Mes signatures »).
export interface CardPrepareRequest {
  certificate: string
  certificateChain: string[]
}

export interface CardPrepareResponse {
  digest: string
  hashFunction: CardHashFunction
  signingTime: string
  documentDigest: string
}

export interface CardCompleteRequest extends CardPrepareRequest {
  signingTime: string
  documentDigest: string
  signature: string
}
