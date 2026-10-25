package be.myrmacta.infrastructure.web.auth;

import java.util.List;

/**
 * Réponse {@code GET /api/auth/me} — rôles sans préfixe {@code ROLE_}. {@code defaultLocale} : langue par défaut du tenant (D7), celle
 * de l'interface tant que l'utilisateur n'a pas choisi la sienne.
 */
public record AuthUserResponse(String id, String name, String email, List<String> roles, String defaultLocale) {}
