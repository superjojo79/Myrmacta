package be.myrmacta.domain.ebox.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TODO §155 (D-EBOX-SEND-MODEL) — document d'un envoi : la pièce principale ({@code mainContent}) ou une annexe. Le nom part tel
 * quel dans l'en-tête {@code Content-Disposition} du multipart : ni guillemet, ni barre oblique inverse, ni caractère de contrôle.
 */
public record EboxDocument(String fileName, String mediaType, byte[] content, boolean mainContent) {

    private static final Pattern FILE_NAME = Pattern.compile("[^\\p{Cntrl}\"\\\\/]{1,200}");
    private static final Pattern MEDIA_TYPE = Pattern.compile("[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}");

    public EboxDocument {
        if (fileName == null || !FILE_NAME.matcher(fileName.strip()).matches()) {
            throw new IllegalArgumentException("document fileName is missing or contains forbidden characters");
        }
        fileName = fileName.strip();
        if (mediaType == null || !MEDIA_TYPE.matcher(mediaType.strip().toLowerCase(java.util.Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("document mediaType is not a valid media type");
        }
        mediaType = mediaType.strip().toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(content, "content");
        if (content.length == 0) {
            throw new IllegalArgumentException("document content is empty");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public long size() {
        return content.length;
    }

    /** Empreinte SHA-256 en hexadécimal : journal d'audit (ATREQ) et contrôle d'intégrité côté eBox. */
    public String sha256Hex() {
        return HexFormat.of().formatHex(sha256());
    }

    public byte[] sha256() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EboxDocument that && mainContent == that.mainContent && fileName.equals(that.fileName)
                && mediaType.equals(that.mediaType) && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, mediaType, Arrays.hashCode(content), mainContent);
    }

    @Override
    public String toString() {
        return "EboxDocument[fileName=" + fileName + ", mediaType=" + mediaType + ", size=" + content.length + ", mainContent="
                + mainContent + "]";
    }
}
