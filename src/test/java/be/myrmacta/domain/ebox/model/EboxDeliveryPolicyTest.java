package be.myrmacta.domain.ebox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.ebox.model.EboxConsent.Level;
import org.junit.jupiter.api.Test;

/** TODO §155 (D-EBOX-SEND-CONSENT) — le poids du consentement diffère entre citoyen et entreprise. */
class EboxDeliveryPolicyTest {

    @Test
    void aCitizenReceivesInHisEboxOnlyWithHisConsent() {
        assertThat(EboxDelivery.decide(EboxRoute.CITIZEN, Level.CONSENTED)).isEqualTo(EboxDelivery.EBOX);
        assertThat(EboxDelivery.decide(EboxRoute.CITIZEN, Level.NO_CONSENT)).isEqualTo(EboxDelivery.FALLBACK);
        assertThat(EboxDelivery.decide(EboxRoute.CITIZEN, Level.NO_EBOX)).isEqualTo(EboxDelivery.FALLBACK);
    }

    @Test
    void anEnterpriseNeedsNoConsent_butAPaperCopyWhenEboxIsNotExclusive() {
        assertThat(EboxDelivery.decide(EboxRoute.ENTERPRISE, Level.EXCLUSIVE_EBOX)).isEqualTo(EboxDelivery.EBOX);
        assertThat(EboxDelivery.decide(EboxRoute.ENTERPRISE, Level.EBOX_NOT_EXCLUSIVE)).isEqualTo(EboxDelivery.EBOX_AND_FALLBACK);
        assertThat(EboxDelivery.decide(EboxRoute.ENTERPRISE, Level.NO_EBOX)).isEqualTo(EboxDelivery.FALLBACK);
    }

    @Test
    void levelsOfTheOtherRouteAreRefused() {
        assertThatThrownBy(() -> EboxDelivery.decide(EboxRoute.CITIZEN, Level.EXCLUSIVE_EBOX)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EboxDelivery.decide(EboxRoute.ENTERPRISE, Level.CONSENTED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyTheEboxDeliveriesPublish() {
        assertThat(EboxDelivery.EBOX.publishes()).isTrue();
        assertThat(EboxDelivery.EBOX_AND_FALLBACK.publishes()).isTrue();
        assertThat(EboxDelivery.FALLBACK.publishes()).isFalse();
        assertThat(EboxDelivery.EBOX.needsFallback()).isFalse();
        assertThat(EboxDelivery.EBOX_AND_FALLBACK.needsFallback()).isTrue();
    }
}
