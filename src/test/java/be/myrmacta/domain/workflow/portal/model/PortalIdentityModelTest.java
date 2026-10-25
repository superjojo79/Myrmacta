package be.myrmacta.domain.workflow.portal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** TODO §104 (issue #210, lot B) — NRN (N3), niveau (T4), correspondance (T5-T7), trace (T8), champ d'identité (T2). */
class PortalIdentityModelTest {

    private static final String NRN = "85073003328";

    @Test
    void aNationalRegisterNumberIsElevenDigitsOnceSeparatorsAreRemoved_N3() {
        assertThat(NationalRegisterNumber.parse("85.07.30-033.28")).map(NationalRegisterNumber::digits).hasValue(NRN);
        assertThat(NationalRegisterNumber.parse(" 85073003328 ")).map(NationalRegisterNumber::digits).hasValue(NRN);
        for (Object invalid : new Object[] {null, "", "  ", "8507300332", "850730033289", "8507300332A", 85073003328L,
                Map.of("nrn", NRN)}) {
            assertThat(NationalRegisterNumber.parse(invalid)).as(String.valueOf(invalid)).isEmpty();
        }
    }

    @Test
    void aNationalRegisterNumberNeverPrintsItsDigits_N2() {
        assertThat(NationalRegisterNumber.parse(NRN).orElseThrow().toString()).doesNotContain("850730").doesNotContain("3328");
        VerifiedIdentity identity = new VerifiedIdentity("fas", IdentityAssuranceLevel.HIGH, "Alice", "Martin",
                NationalRegisterNumber.parse(NRN).orElseThrow());
        assertThat(identity.toString()).doesNotContain("850730").doesNotContain("3328");
    }

    @Test
    void onlySubstantialAndHighAreAccepted_T4() {
        assertThat(IdentityAssuranceLevel.HIGH.isAccepted()).isTrue();
        assertThat(IdentityAssuranceLevel.SUBSTANTIAL.isAccepted()).isTrue();
        assertThat(IdentityAssuranceLevel.LOW.isAccepted()).isFalse();
    }

    @Test
    void theMatchComparesTheVerifiedNumberWithTheIdentityField_T5_T6_T7() {
        NationalRegisterNumber verified = NationalRegisterNumber.parse(NRN).orElseThrow();
        NationalRegisterNumber other = NationalRegisterNumber.parse("90011512462").orElseThrow();

        assertThat(IdentityMatch.of("85.07.30-033.28", verified)).isEqualTo(IdentityMatch.CONFORME);
        assertThat(IdentityMatch.of(NRN, other)).isEqualTo(IdentityMatch.NON_CONFORME);
        assertThat(IdentityMatch.of(null, verified)).isEqualTo(IdentityMatch.PREMIER_ENREGISTREMENT);
        assertThat(IdentityMatch.of("  ", verified)).isEqualTo(IdentityMatch.PREMIER_ENREGISTREMENT);
        assertThat(IdentityMatch.of(NRN, null)).as("identité sans NRN, NRN attendu").isEqualTo(IdentityMatch.NON_CONFORME);
        assertThat(IdentityMatch.of(null, null)).as("identité sans NRN, champ vide").isEqualTo(IdentityMatch.SANS_NRN);
        assertThat(IdentityMatch.of("pas un NRN", verified)).as("valeur attendue illisible").isEqualTo(IdentityMatch.NON_CONFORME);

        assertThat(IdentityMatch.NON_CONFORME.allowsSubmission()).isFalse();
        assertThat(IdentityMatch.CONFORME.allowsSubmission()).isTrue();
        assertThat(IdentityMatch.PREMIER_ENREGISTREMENT.writesTheField()).isTrue();
        assertThat(IdentityMatch.CONFORME.writesTheField()).isFalse();
        assertThat(IdentityMatch.SANS_NRN.writesTheField()).isFalse();
    }

    @Test
    void theTraceKeepsNameProviderLevelAndResultButNoNumber_T8() {
        Instant at = Instant.parse("2026-10-24T10:00:00Z");
        VerifiedIdentity identity = new VerifiedIdentity("itsme", IdentityAssuranceLevel.SUBSTANTIAL, "Alice", "Martin",
                NationalRegisterNumber.parse(NRN).orElseThrow());

        IdentityVerification trace = IdentityVerification.of(identity, IdentityMatch.CONFORME, at);

        assertThat(trace).isEqualTo(new IdentityVerification("itsme", IdentityAssuranceLevel.SUBSTANTIAL, "Alice", "Martin",
                IdentityMatch.CONFORME, at));
        assertThat(trace.toString()).doesNotContain(NRN);
    }

    @Test
    void theIdentityFieldIsADossierFieldSource_T2() {
        assertThat(PortalIdentityField.parse("dossierField:nrnDemandeur")).map(PortalIdentityField::fieldName)
                .hasValue("nrnDemandeur");
        assertThat(PortalIdentityField.parse(" dossierField:nrn_1 ")).map(PortalIdentityField::fieldName).hasValue("nrn_1");
        for (String invalid : new String[] {null, "", "variable:nrn", "nrnDemandeur", "dossierField:", "dossierField:a.b",
                "dossierField:${x}"}) {
            assertThat(PortalIdentityField.parse(invalid)).as(String.valueOf(invalid)).isEmpty();
        }
        assertThatThrownBy(() -> new PortalIdentityField("a-b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aVerifiedIdentityRequiresProviderAndLevel() {
        assertThatThrownBy(() -> new VerifiedIdentity(null, IdentityAssuranceLevel.HIGH, "A", "B", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerifiedIdentity("fas", null, "A", "B", null)).isInstanceOf(NullPointerException.class);
        assertThat(new VerifiedIdentity("fas", IdentityAssuranceLevel.HIGH, null, null, null).nrn()).isEmpty();
    }
}
