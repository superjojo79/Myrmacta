/** Un jeton émis par le serveur fait 43 caractères base64url ; marge large, alphabet strict. */
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{20,128}$/

/**
 * D-PORTAL-CSRF-SESSION — le jeton arrive dans le fragment d'URL (`#`), jamais envoyé au serveur ni
 * au `Referer`. Il est lu une fois puis effacé de la barre d'adresse et de l'historique
 * (`replaceState`) ; il ne vit ensuite qu'en mémoire (pas de stockage navigateur).
 */
export function takeTokenFromLocation(
  location: Pick<Location, 'hash' | 'pathname' | 'search'>,
  history: Pick<History, 'replaceState'>,
): string | null {
  const raw = location.hash.startsWith('#') ? location.hash.slice(1) : location.hash
  if (raw) {
    history.replaceState(null, '', location.pathname + location.search)
  }
  return TOKEN_PATTERN.test(raw) ? raw : null
}

/**
 * TODO §104 (D-BEAUTH-PROOF) — `identity` : retour du fournisseur d'identité, `token` est alors la preuve d'identité
 * qui remplace le jeton du lien ; `identity-refused` : refus neutre, sans jeton.
 */
export type PortalLink =
  | { kind: 'task' | 'signature' | 'identity'; token: string }
  | { kind: 'identity-refused'; token: null }

const SIGNATURE_PREFIX = 'signature:'
const IDENTITY_PREFIX = 'identity:'
const IDENTITY_REFUSED = 'identity-refused'

/**
 * TODO §72 — le lien de signature est `#signature:<jeton>` ; le lien de tâche (§68) reste `#<jeton>` ; le retour d'une
 * authentification forte (§104) est `#identity:<preuve>` ou `#identity-refused`. Même règle : lu une fois, effacé de la
 * barre d'adresse, jamais stocké.
 */
export function takeLinkFromLocation(
  location: Pick<Location, 'hash' | 'pathname' | 'search'>,
  history: Pick<History, 'replaceState'>,
): PortalLink | null {
  const raw = location.hash.startsWith('#') ? location.hash.slice(1) : location.hash
  if (raw === IDENTITY_REFUSED) {
    history.replaceState(null, '', location.pathname + location.search)
    return { kind: 'identity-refused', token: null }
  }
  const prefix = raw.startsWith(SIGNATURE_PREFIX) ? SIGNATURE_PREFIX : raw.startsWith(IDENTITY_PREFIX) ? IDENTITY_PREFIX : ''
  const kind = prefix === SIGNATURE_PREFIX ? 'signature' : prefix === IDENTITY_PREFIX ? 'identity' : 'task'
  const token = takeTokenFromLocation({ ...location, hash: '#' + raw.slice(prefix.length) }, history)
  return token ? { kind, token } : null
}
