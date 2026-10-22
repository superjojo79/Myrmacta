import type { NavigationGuard } from 'vue-router'

/**
 * Garde de rôles du routeur. Sur une route protégée, attend le chargement de l'utilisateur
 * (`/api/auth/me`) avant de décider : au chargement direct d'une route (lien profond, F5), la
 * navigation initiale précède la réponse de `/me`, et laisser passer « tant que l'utilisateur
 * n'est pas connu » affichait l'écran à un rôle non autorisé. L'API reste l'autorité (403).
 */
export function createRoleGuard(
  ensureUserLoaded: () => Promise<void>,
  currentRoles: () => string[] | null,
): NavigationGuard {
  return async (to) => {
    const required = to.meta.roles as string[] | undefined
    if (!required?.length) return true
    await ensureUserLoaded()
    const roles = currentRoles()
    const ok = roles != null && required.some((role) => roles.includes(role))
    return ok ? true : { name: 'supervision' }
  }
}
