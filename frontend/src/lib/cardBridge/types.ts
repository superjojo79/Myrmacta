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

/**
 * Un choix possible de l'utilisateur : un certificat de signature, sur un lecteur. Web eID n'en rend qu'un (son logiciel
 * natif affiche lui-même le choix) ; BeIDConnect en rend un par couple lecteur × certificat.
 */
export interface CardChoice {
  id: string
  /** Certificat feuille ; la chaîne s'obtient par `certificateOf`. */
  certificate: CardCertificate
  readerName: string | null
  /** Titulaire lu dans le certificat (nom, jamais le numéro de registre national) ; `null` si illisible. */
  holder: string | null
  /** Fin de validité, ISO-8601 UTC ; `null` si illisible. */
  notAfter: string | null
  /** Vrai quand la page doit demander le PIN (BeIDConnect sur un lecteur sans clavier) ; avec Web eID, jamais. */
  needsPin: boolean
}

export interface CardBridge {
  readonly id: CardBridgeId
  /** Vérifie que le pont répond ; rejette avec une `CardBridgeError` (indisponible, à mettre à jour, contexte non sécurisé). */
  detect(): Promise<void>
  /** Les choix de signature offerts par la carte ou les cartes présentes (au moins un, sinon une erreur). */
  listChoices(): Promise<CardChoice[]>
  /** Le certificat du choix retenu, avec sa chaîne quand le pont la donne. */
  certificateOf(choice: CardChoice): Promise<CardCertificate>
  /** `pin` est `null` quand `choice.needsPin` est faux ; le PIN n'est utilisé que pour cet appel. Rend la signature en base64. */
  sign(choice: CardChoice, digest: string, hashFunction: CardHashFunction, pin: string | null): Promise<string>
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
