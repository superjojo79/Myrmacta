package be.myrmacta.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TODO §114 (D-GROUP-SOURCE) — nom de la claim du jeton OIDC qui porte les groupes de l'utilisateur ({@code groups} chez
 * Keycloak avec le mapper « Group Membership » et chez Microsoft Entra ID). Lue à chaque connexion ; absente : aucun groupe.
 */
@ConfigurationProperties(prefix = "app.security.oidc")
public record OidcGroupsProperties(@DefaultValue("groups") String groupsClaim) {

    public OidcGroupsProperties {
        groupsClaim = groupsClaim == null || groupsClaim.isBlank() ? "groups" : groupsClaim.strip();
    }
}
