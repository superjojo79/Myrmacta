package be.myrmacta.domain.ebox.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TODO §155 (D-EBOX-SEND-MODEL) — document d'un envoi : la pièce principale ({@code mainContent}) ou une annexe. Le nom part tel
 * quel dans l'en-tête {@code Content-Disposition} du multipart : ni guillemet, ni barre oblique inverse, ni caractère de contrôle.
 *
 * <p>Une pièce peut peser jusqu'à 100 Mo : le contenu n'est copié qu'une fois, à la construction, et son empreinte SHA-256 n'est
 * calculée qu'une fois. {@link #copyInto} l'écrit dans le corps de la requête sans copie intermédiaire ; {@link #content()} rend
 * une copie, réservée aux petits usages (tests, aperçu).
 */
public final class EboxDocument {

    private static final Pattern FILE_NAME = Pattern.compile("[^\\p{Cntrl}\"\\\\/]{1,200}");
    private static final Pattern MEDIA_TYPE = Pattern.compile("[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}");

    private final String fileName;
    private final String mediaType;
    private final byte[] content;
    private final boolean mainContent;
    private final byte[] sha256;

    public EboxDocument(String fileName, String mediaType, byte[] content, boolean mainContent) {
        if (fileName == null || !FILE_NAME.matcher(fileName.strip()).matches()) {
            throw new IllegalArgumentException("document fileName is missing or contains forbidden characters");
        }
        String type = mediaType == null ? "" : mediaType.strip().toLowerCase(Locale.ROOT);
        if (!MEDIA_TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("document mediaType is not a valid media type");
        }
        Objects.requireNonNull(content, "content");
        if (content.length == 0) {
            throw new IllegalArgumentException("document content is empty");
        }
        this.fileName = fileName.strip();
        this.mediaType = type;
        this.content = content.clone();
        this.mainContent = mainContent;
        try {
            this.sha256 = MessageDigest.getInstance("SHA-256").digest(this.content);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public String fileName() {
        return fileName;
    }

    public String mediaType() {
        return mediaType;
    }

    public boolean mainContent() {
        return mainContent;
    }

    /** Copie du contenu : à éviter pour une grosse pièce, préférer {@link #copyInto}. */
    public byte[] content() {
        return content.clone();
    }

    /** Écrit le contenu dans {@code target} à partir de {@code offset}, sans copie intermédiaire. */
    public void copyInto(byte[] target, int offset) {
        System.arraycopy(content, 0, target, offset, content.length);
    }

    public long size() {
        return content.length;
    }

    /** Empreinte SHA-256 en hexadécimal : journal d'audit (ATREQ) et contrôle d'intégrité côté eBox. */
    public String sha256Hex() {
        return HexFormat.of().formatHex(sha256);
    }

    public byte[] sha256() {
        return sha256.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EboxDocument that && mainContent == that.mainContent && fileName.equals(that.fileName)
                && mediaType.equals(that.mediaType) && Arrays.equals(sha256, that.sha256) && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, mediaType, Arrays.hashCode(sha256), mainContent);
    }

    @Override
    public String toString() {
        return "EboxDocument[fileName=" + fileName + ", mediaType=" + mediaType + ", size=" + content.length + ", mainContent="
                + mainContent + "]";
    }
}
