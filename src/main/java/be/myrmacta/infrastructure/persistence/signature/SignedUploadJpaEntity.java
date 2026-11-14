package be.myrmacta.infrastructure.persistence.signature;

import be.myrmacta.domain.signature.model.SignedUpload;
import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Téléversement accepté d'un PDF signé hors de Myrmacta (TODO §138). Les constats sont ceux de la
 * vérification (D-EID-EVIDENCE) ; jamais le numéro de registre national (D-EID-NRN). Les libellés lus dans
 * un certificat sont du texte libre : ceux qui ont une borne en base sont tronqués avant l'écriture, pour
 * qu'un certificat bavard ne fasse pas échouer l'enregistrement d'une signature par ailleurs valide.
 */
@Entity
@Table(name = "signature_signed_upload")
class SignedUploadJpaEntity {

    private static final int FORMAT_LENGTH = 64;
    private static final int ALGORITHM_LENGTH = 64;
    private static final int REVOCATION_SOURCE_LENGTH = 16;

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "signer_id", nullable = false)
    private UUID signerId;

    @Column(name = "sequence_number", nullable = false)
    private int sequence;

    @Column(name = "storage_ref", nullable = false, length = 512)
    private String storageRef;

    @Column(name = "digest", nullable = false, length = 64)
    private String digest;

    @Column(name = "signer_name", nullable = false)
    private String signerName;

    @Column(name = "certificate_issuer", nullable = false)
    private String certificateIssuer;

    @Column(name = "certificate_serial", nullable = false)
    private String certificateSerial;

    @Column(name = "certificate_fingerprint", nullable = false, length = 64)
    private String certificateFingerprint;

    @Column(name = "signing_time")
    private Instant signingTime;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "signature_format", length = FORMAT_LENGTH)
    private String signatureFormat;

    @Column(name = "signature_algorithm", length = ALGORITHM_LENGTH)
    private String signatureAlgorithm;

    @Column(name = "qualified_certificate", nullable = false)
    private boolean qualifiedCertificate;

    @Column(name = "qualified_device", nullable = false)
    private boolean qualifiedDevice;

    @Column(name = "revocation_source", length = REVOCATION_SOURCE_LENGTH)
    private String revocationSource;

    @Column(name = "revocation_produced_at")
    private Instant revocationProducedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SignedUploadJpaEntity() {}

    static SignedUploadJpaEntity fromDomain(SignedUpload u) {
        SignedUploadEvidence ev = u.evidence();
        SignedUploadJpaEntity e = new SignedUploadJpaEntity();
        e.id = u.id();
        e.tenantId = u.tenantId();
        e.requestId = u.requestId();
        e.signerId = u.signerId();
        e.sequence = u.sequence();
        e.storageRef = u.storageRef();
        e.digest = u.digest();
        e.signerName = ev.signerName();
        e.certificateIssuer = ev.certificateIssuer();
        e.certificateSerial = ev.certificateSerial();
        e.certificateFingerprint = ev.certificateFingerprint();
        e.signingTime = ev.signingTime();
        e.verifiedAt = ev.verifiedAt();
        e.signatureFormat = cut(ev.signatureFormat(), FORMAT_LENGTH);
        e.signatureAlgorithm = cut(ev.signatureAlgorithm(), ALGORITHM_LENGTH);
        e.qualifiedCertificate = ev.qualifiedCertificate();
        e.qualifiedDevice = ev.qualifiedDevice();
        e.revocationSource = cut(ev.revocationSource(), REVOCATION_SOURCE_LENGTH);
        e.revocationProducedAt = ev.revocationProducedAt();
        e.createdAt = u.createdAt();
        return e;
    }

    SignedUpload toDomain() {
        return new SignedUpload(id, tenantId, requestId, signerId, sequence, storageRef, digest,
                new SignedUploadEvidence(signerName, certificateIssuer, certificateSerial, certificateFingerprint,
                        signingTime, verifiedAt, signatureFormat, signatureAlgorithm, qualifiedCertificate,
                        qualifiedDevice, revocationSource, revocationProducedAt),
                createdAt);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
