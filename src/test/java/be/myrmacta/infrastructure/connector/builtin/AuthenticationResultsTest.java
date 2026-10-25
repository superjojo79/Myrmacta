package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Revue D3c/D3d (H-A) — l'attestation de l'expéditeur : serveur reconnu, résultat lu sur le bon jeton, alignement sur « De ». */
class AuthenticationResultsTest {

    private static final String SERVER = "mx.example.com";
    private static final String FROM = "client@example.org";

    private static boolean attests(String header) {
        return AuthenticationResults.attests(header, SERVER, FROM);
    }

    @Test
    void aDmarcPassOrAnAlignedDkimOrSpfPassAttestsTheSender() {
        assertThat(attests("mx.example.com; dmarc=pass header.from=example.org")).isTrue();
        assertThat(attests("mx.example.com; dkim=pass header.d=example.org header.s=sel")).isTrue();
        assertThat(attests("mx.example.com; dkim=pass header.d=mail.example.org")).isTrue(); // sous-domaine : alignement souple
        assertThat(attests("mx.example.com; spf=pass smtp.mailfrom=bounce@example.org")).isTrue();
        assertThat(attests("MX.EXAMPLE.COM; DKIM=PASS header.d=EXAMPLE.ORG")).isTrue();
        assertThat(attests("mx.example.com 1; dmarc=pass")).isTrue(); // authserv-id suivi d'une version
    }

    @Test
    void aPassThatIsNotAlignedWithTheFromDomainProvesNothing() {
        // le domaine de l'attaquant a un SPF et un DKIM valides, « De » usurpe un autre domaine
        assertThat(attests("mx.example.com; spf=pass smtp.mailfrom=attacker.com; dkim=pass header.d=attacker.com; dmarc=none")).isFalse();
        assertThat(attests("mx.example.com; dkim=pass header.d=notexample.org")).isFalse(); // simple suffixe de texte
    }

    @Test
    void theResultIsReadOnTheFirstTokenNeverInsideAProperty() {
        // un MAIL FROM « dkim=pass@… » se recopie dans smtp.mailfrom : ce n'est pas un résultat
        assertThat(attests("mx.example.com; spf=none smtp.mailfrom=dkim=pass@nospf.example; dkim=none; dmarc=none header.from=example.org")).isFalse();
        assertThat(attests("mx.example.com; dkim=none header.d=example.org (dkim=pass)")).isFalse();
    }

    @Test
    void aHeaderFromAnotherServerOrWithoutAServerIdIsIgnored() {
        assertThat(attests("attacker-forged; dkim=pass header.d=example.org")).isFalse();
        assertThat(attests("evil.example; dmarc=pass")).isFalse();
        assertThat(AuthenticationResults.attests("mx.example.com; dmarc=pass", null, FROM)).isFalse();
        assertThat(AuthenticationResults.attests("mx.example.com; dmarc=pass", " ", FROM)).isFalse();
        assertThat(attests(null)).isFalse();
        assertThat(attests("")).isFalse();
    }

    @Test
    void anyFailureCancelsAnyPass() {
        assertThat(attests("mx.example.com; dkim=pass header.d=example.org; spf=fail smtp.mailfrom=example.org")).isFalse();
        assertThat(attests("mx.example.com; dmarc=pass; dkim=temperror")).isFalse();
    }

    @Test
    void anUnusableFromAddressAttestsNothing() {
        assertThat(AuthenticationResults.attests("mx.example.com; dmarc=pass", SERVER, "")).isFalse();
        assertThat(AuthenticationResults.attests("mx.example.com; dmarc=pass", SERVER, null)).isFalse();
    }
}
