package be.myrmacta.domain.precheck.port.out;

/** TODO §106 (P8) — la pré-vérification est-elle activée sur l'instance ({@code app.precheck.enabled}) ? */
public interface PrecheckAvailabilityPort {

    boolean enabled();
}
