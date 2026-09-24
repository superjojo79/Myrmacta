package com.flowforge.infrastructure.web.portal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * TODO §68 (Phase 7) — {@code /portal} sans slash final servait {@code portal/index.html} dont les
 * assets relatifs ({@code ./assets/…}) se résolvaient alors vers {@code /assets/…} (bundle du SPA
 * interne, chaîne authentifiée) : page cassée. Redirection vers {@code /portal/} ; le navigateur
 * conserve le fragment {@code #jeton} à travers une redirection sans fragment (RFC 9110 §10.2.2).
 */
@Controller
@ConditionalOnProperty(prefix = "app.portal", name = "enabled", havingValue = "true")
class PortalPageRedirectController {

    @GetMapping("/portal")
    String redirectToSlash() {
        return "redirect:/portal/";
    }
}
