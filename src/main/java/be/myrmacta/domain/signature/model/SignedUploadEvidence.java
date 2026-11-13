package be.myrmacta.domain.signature.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Ce que la vérification d'un PDF signé téléversé a établi (TODO §138, D-EID-EVIDENCE), repris dans
 * la preuve de signature.
 *
 * <p>{@code signerName} vient du certificat (prénoms et nom). Le numéro de registre national que
 * porte le certificat eID n'est jamais extrait ni conservé à part (D-EID-NRN) ; il reste dans le
 * PDF signé, qui contient le certificat. {@code certificateFingerprint} est le SHA-256 du
 * certificat, en hexadécimal minuscule.
 *
 * @param signingTime heure déclarée par le signataire, non prouvée ; peut être nulle
 * @param verifiedAt heure de la vérification par Myrmacta
 * @param signatureFormat format constaté, par exemple {@code PAdES-BASELINE-B}
 * @param qualifiedCertificate le certificat se déclare qualifié (QcCompliance)
 * @param qualifiedDevice la clé est déclarée sur dispositif qualifié (QcSSCD)
 * @param revocationSource {@code OCSP} ou {@code CRL}
 */
public record SignedUploadEvidence(
        String signerName,
        String certificateIssuer,
        String certificateSerial,
        String certificateFingerprint,
        Instant signingTime,
        Instant verifiedAt,
        String signatureFormat,
        String signatureAlgorithm,
        boolean qualifiedCertificate,
        boolean qualifiedDevice,
        String revocationSource,
        Instant revocationProducedAt) {

    public SignedUploadEvidence {
        Objects.requireNonNull(signerName, "signerName");
        Objects.requireNonNull(certificateIssuer, "certificateIssuer");
        Objects.requireNonNull(certificateSerial, "certificateSerial");
        Objects.requireNonNull(certificateFingerprint, "certificateFingerprint");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
    }
}
