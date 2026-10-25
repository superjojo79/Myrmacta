package be.myrmacta.domain.dossier.port.out;

public interface DossierReferenceGenerator {
    String generate(String tenantId, String dossierTypeCode);
}
