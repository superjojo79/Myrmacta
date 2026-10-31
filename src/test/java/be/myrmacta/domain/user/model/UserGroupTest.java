package be.myrmacta.domain.user.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** TODO §114 (D-GROUP-SOURCE) — un groupe : code stable, libellé lisible, groupe d'IdP repris facultatif, membres bornés. */
class UserGroupTest {

    private static UserGroup group(String code, String label, String idpGroup, Set<String> members) {
        return new UserGroup("tenant_dev", code, label, idpGroup, members);
    }

    @Test
    void aValidGroupKeepsItsValues_andHasNoMembersByDefault() {
        UserGroup group = group("COMPTABILITE", "Comptabilité", "/services/compta", null);

        assertThat(group.code()).isEqualTo("COMPTABILITE");
        assertThat(group.label()).isEqualTo("Comptabilité");
        assertThat(group.idpGroup()).isEqualTo("/services/compta");
        assertThat(group.memberIds()).isEmpty();
    }

    @Test
    void aBlankIdpGroupMeansNone() {
        assertThat(group("RH", "Ressources humaines", "  ", Set.of()).idpGroup()).isNull();
    }

    @Test
    void theCodeIsAnUppercaseIdentifier() {
        for (String bad : new String[] {"", "rh", "1RH", "RH-PAIE", "R", "A".repeat(UserGroup.MAX_CODE_CHARS + 1)}) {
            assertThatThrownBy(() -> group(bad, "x", null, Set.of())).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(group("RH_PAIE2", "Paie", null, Set.of()).code()).isEqualTo("RH_PAIE2");
    }

    @Test
    void theLabelIsRequiredBoundedAndPrintable() {
        assertThatThrownBy(() -> group("RH", " ", null, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> group("RH", "x".repeat(UserGroup.MAX_LABEL_CHARS + 1), null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> group("RH", "Ressources\nhumaines", null, Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theIdpGroupIsBoundedAndPrintable() {
        assertThatThrownBy(() -> group("RH", "RH", "g".repeat(UserGroup.MAX_IDP_GROUP_CHARS + 1), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> group("RH", "RH", "rh\u0000", Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theMembersAreBounded() {
        Set<String> tooMany = new HashSet<>(IntStream.rangeClosed(0, UserGroup.MAX_MEMBERS).mapToObj(i -> "u" + i).toList());

        assertThatThrownBy(() -> group("RH", "RH", null, tooMany)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMemberIsListedLocallyOrThroughItsIdpGroup() {
        UserGroup group = group("COMPTABILITE", "Comptabilité", "/services/compta", Set.of("alice"));

        assertThat(group.includes("alice", Set.of())).isTrue();
        assertThat(group.includes("bob", Set.of("/services/compta"))).isTrue();
        assertThat(group.includes("carol", Set.of("/services/rh"))).isFalse();
        assertThat(group("RH", "RH", null, Set.of()).includes("dave", Set.of("RH"))).isFalse();
    }
}
