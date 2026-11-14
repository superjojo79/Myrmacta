package be.myrmacta.infrastructure.signature.eid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Autorités de confiance du fournisseur {@code eid} (TODO §138, D-EID-TRUST) : Myrmacta n'embarque aucun
 * certificat. L'exploitant fournit, dans un fichier PEM, les racines et les autorités intermédiaires qui
 * émettent les certificats qu'il accepte. Sans fichier lisible contenant au moins un certificat, le
 * fournisseur refuse de démarrer : une instance sans autorité n'accepterait rien, et le dirait trop tard.
 */
public final class EidTrustAnchors {

    /** Propriété à renseigner, citée dans les messages d'erreur. */
    public static final String PROPERTY = "app.signature.eid.trust-anchors-file";

    private final List<X509Certificate> certificates;

    private EidTrustAnchors(List<X509Certificate> certificates) {
        this.certificates = List.copyOf(certificates);
    }

    /**
     * Lit un fichier PEM d'une ou plusieurs autorités.
     *
     * @param file chemin du fichier ; nul ou vide : refusé
     * @throws IllegalStateException fichier absent, illisible, ou sans aucun certificat X.509
     */
    public static EidTrustAnchors load(String file) {
        if (file == null || file.isBlank()) {
            throw new IllegalStateException(
                    PROPERTY + " is required when app.signature.provider=eid: the PEM file of the trusted "
                            + "certificate authorities (D-EID-TRUST)");
        }
        Path path;
        try {
            path = Path.of(file.trim());
        } catch (RuntimeException ex) {
            throw new IllegalStateException(PROPERTY + " is not a valid path", ex);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(PROPERTY + ": no such file " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalStateException(PROPERTY + ": file " + path + " is not readable");
        }
        List<X509Certificate> parsed = new ArrayList<>();
        try (InputStream in = Files.newInputStream(path)) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            for (java.security.cert.Certificate certificate : factory.generateCertificates(in)) {
                if (certificate instanceof X509Certificate x509) {
                    parsed.add(x509);
                }
            }
        } catch (IOException | CertificateException ex) {
            throw new IllegalStateException(
                    PROPERTY + ": file " + path + " holds no readable PEM certificate (" + ex.getClass().getSimpleName()
                            + ")",
                    ex);
        }
        if (parsed.isEmpty()) {
            throw new IllegalStateException(PROPERTY + ": file " + path + " holds no certificate");
        }
        return new EidTrustAnchors(parsed);
    }

    public static EidTrustAnchors of(List<X509Certificate> certificates) {
        Objects.requireNonNull(certificates, "certificates");
        if (certificates.isEmpty()) {
            throw new IllegalStateException(PROPERTY + ": no certificate");
        }
        return new EidTrustAnchors(certificates);
    }

    /** Autorités de confiance, dans l'ordre du fichier. */
    public List<X509Certificate> certificates() {
        return certificates;
    }
}
