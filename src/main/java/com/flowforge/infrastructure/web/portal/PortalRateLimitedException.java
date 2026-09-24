package com.flowforge.infrastructure.web.portal;

/** Plafond par IP atteint avant toute recherche de jeton (T-ACCESS-4). */
class PortalRateLimitedException extends RuntimeException {

    PortalRateLimitedException() {
        super("portal rate limit exceeded");
    }
}
