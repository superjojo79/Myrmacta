package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Couverture de branches manquante trouvée en clôturant fiabilisation-tests-plateforme
 * (D-COVERAGE, seuil BRANCH relevé) : {@link M2mJwtAuthoritiesConverter} n'avait aucun test.
 */
class M2mJwtAuthoritiesConverterTest {

    private final M2mJwtAuthoritiesConverter converter = new M2mJwtAuthoritiesConverter();

    @Test
    void convert_rolesClaim_addsRoleAndRolePrefixedAuthority() {
        Jwt jwt = jwt(Map.of("roles", List.of("API_SERVICE")));

        List<String> authorities = names(converter.convert(jwt));

        assertThat(authorities).containsExactlyInAnyOrder("API_SERVICE", "ROLE_API_SERVICE");
    }

    @Test
    void convert_roleAlreadyPrefixedWithRole_doesNotDoublePrefix() {
        Jwt jwt = jwt(Map.of("roles", List.of("ROLE_ADMIN")));

        List<String> authorities = names(converter.convert(jwt));

        assertThat(authorities).containsExactly("ROLE_ADMIN");
    }

    @Test
    void convert_blankOrNullRoleEntries_areSkipped() {
        Jwt jwt = jwt(Map.of("roles", java.util.Arrays.asList("  ", null)));

        List<String> authorities = names(converter.convert(jwt));

        assertThat(authorities).isEmpty();
    }

    @Test
    void convert_rolesClaimNotACollection_isIgnored() {
        Jwt jwt = jwt(Map.of("roles", "not-a-collection"));

        List<String> authorities = names(converter.convert(jwt));

        assertThat(authorities).isEmpty();
    }

    @Test
    void convert_scopeClaim_splitsOnWhitespaceAndAddsScopePrefixedAuthority() {
        Jwt jwt = jwt(Map.of("scope", "read  write"));

        List<String> authorities = names(converter.convert(jwt));

        assertThat(authorities)
                .containsExactlyInAnyOrder("read", "SCOPE_read", "write", "SCOPE_write");
    }

    @Test
    void convert_scopeAlreadyPrefixedWithScope_doesNotDoublePrefix() {
        Jwt jwt = jwt(Map.of("scope", "SCOPE_read"));

        List<String> authorities = names(converter.convert(jwt));

        assertThat(authorities).containsExactly("SCOPE_read");
    }

    @Test
    void convert_blankScope_isIgnored() {
        Jwt jwt = jwt(Map.of("scope", "   "));

        List<String> authorities = names(converter.convert(jwt));

        assertThat(authorities).isEmpty();
    }

    @Test
    void convert_scpClaim_addsScopePrefixedAuthorityAndSkipsNullEntries() {
        Jwt jwt = jwt(Map.of("scp", java.util.Arrays.asList("read", null)));

        List<String> authorities = names(converter.convert(jwt));

        assertThat(authorities).containsExactlyInAnyOrder("read", "SCOPE_read");
    }

    @Test
    void convert_noRecognizedClaims_returnsEmptySet() {
        Jwt jwt = jwt(Map.of());

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private static List<String> names(java.util.Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-subject")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600))
                .build();
    }
}
