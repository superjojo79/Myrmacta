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

export type PortalLink = { kind: 'task' | 'signature'; token: string }

const SIGNATURE_PREFIX = 'signature:'

/**
 * TODO §72 — le lien de signature est `#signature:<jeton>` ; le lien de tâche (§68) reste `#<jeton>`.
 * Même règle : lu une fois, effacé de la barre d'adresse, jamais stocké.
 */
export function takeLinkFromLocation(
  location: Pick<Location, 'hash' | 'pathname' | 'search'>,
  history: Pick<History, 'replaceState'>,
): PortalLink | null {
  const raw = location.hash.startsWith('#') ? location.hash.slice(1) : location.hash
  const kind = raw.startsWith(SIGNATURE_PREFIX) ? 'signature' : 'task'
  const token = takeTokenFromLocation(
    { ...location, hash: kind === 'signature' ? '#' + raw.slice(SIGNATURE_PREFIX.length) : location.hash },
    history,
  )
  return token ? { kind, token } : null
}
