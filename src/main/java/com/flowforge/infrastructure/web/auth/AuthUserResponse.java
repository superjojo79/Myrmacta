package com.flowforge.infrastructure.web.auth;

import java.util.List;

/** Réponse {@code GET /api/auth/me} — rôles sans préfixe {@code ROLE_}. */
public record AuthUserResponse(String name, String email, List<String> roles) {}
