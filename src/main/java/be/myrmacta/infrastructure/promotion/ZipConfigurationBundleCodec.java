package be.myrmacta.infrastructure.promotion;

import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.InvalidBundleException;
import be.myrmacta.domain.promotion.port.out.ConfigurationBundleCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §83 (D4, D-PROMO-FORMAT) — archive ZIP : {@code manifest.json} (version du format, source, somme SHA-256 de chaque fichier)
 * et un fichier par artefact. L'écriture est déterministe (ordre fixe, horodatages constants) ; la lecture est défensive : chemins
 * hors périmètre refusés, fichier non déclaré ou somme incorrecte refusés, tailles bornées (archive piégée), format inconnu refusé.
 *
 * <p>La somme garantit l'intégrité, pas l'authenticité : le bundle n'est pas signé (à transporter par un canal de confiance).
 */
public final class ZipConfigurationBundleCodec implements ConfigurationBundleCodec {

    static final String MANIFEST = "manifest.json";
    private static final long FIXED_TIME = 946684800000L; // 2000-01-01T00:00:00Z : archive reproductible
    private static final int MAX_ARCHIVE_ENTRIES = ConfigurationBundle.MAX_ENTRIES + 1;

    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT)
            .enable(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Override
    public byte[] write(ConfigurationBundle bundle) {
        List<BundleEntry> entries = bundle.entries().stream()
                .sorted(Comparator.comparing((BundleEntry e) -> e.type().ordinal()).thenComparing(BundleEntry::key))
                .toList();
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("formatVersion", bundle.formatVersion());
        manifest.put("generatedAt", bundle.generatedAt().toString());
        manifest.put("sourceLabel", bundle.sourceLabel());
        manifest.put("appVersion", bundle.appVersion());
        List<Map<String, String>> artifacts = new ArrayList<>();
        for (BundleEntry e : entries) {
            Map<String, String> a = new LinkedHashMap<>();
            a.put("type", e.type().name());
            a.put("key", e.key());
            a.put("path", e.type().path(e.key()));
            a.put("sha256", e.sha256());
            artifacts.add(a);
        }
        manifest.put("artifacts", artifacts);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, MANIFEST, (mapper.writeValueAsString(manifest) + "\n").getBytes(StandardCharsets.UTF_8));
            for (BundleEntry e : entries) {
                put(zip, e.type().path(e.key()), e.content());
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("cannot write the bundle", e);
        }
    }

    private static void put(ZipOutputStream zip, String path, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(FIXED_TIME);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    @Override
    public ConfigurationBundle read(byte[] archive) {
        if (archive == null || archive.length == 0) {
            throw new InvalidBundleException("the bundle is empty");
        }
        Map<String, byte[]> files = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (files.size() >= MAX_ARCHIVE_ENTRIES) {
                    throw new InvalidBundleException("the archive holds too many files");
                }
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                if (files.containsKey(name)) {
                    throw new InvalidBundleException("duplicate file in the archive: " + name);
                }
                byte[] content = readBounded(zip, name);
                total += content.length;
                if (total > ConfigurationBundle.MAX_TOTAL_BYTES) {
                    throw new InvalidBundleException("the archive expands beyond " + ConfigurationBundle.MAX_TOTAL_BYTES + " bytes");
                }
                files.put(name, content);
            }
        } catch (IOException | IllegalArgumentException e) {
            if (e instanceof InvalidBundleException invalid) {
                throw invalid;
            }
            throw new InvalidBundleException("the file is not a readable bundle archive", e);
        }
        byte[] manifestBytes = files.remove(MANIFEST);
        if (manifestBytes == null) {
            throw new InvalidBundleException("the archive has no " + MANIFEST);
        }
        JsonNode manifest;
        try {
            manifest = mapper.readTree(new String(manifestBytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new InvalidBundleException(MANIFEST + " is not valid JSON", e);
        }
        if (!manifest.isObject() || !manifest.path("formatVersion").isInt()) {
            throw new InvalidBundleException(MANIFEST + " has no formatVersion");
        }
        int formatVersion = manifest.get("formatVersion").asInt();
        JsonNode artifacts = manifest.path("artifacts");
        if (!artifacts.isArray()) {
            throw new InvalidBundleException(MANIFEST + " lists no artifacts");
        }
        List<BundleEntry> entries = new ArrayList<>();
        Set<String> declaredPaths = new HashSet<>();
        for (JsonNode a : artifacts) {
            ArtifactType type;
            try {
                type = ArtifactType.valueOf(a.path("type").asString(""));
            } catch (IllegalArgumentException e) {
                throw new InvalidBundleException("unknown artifact type in the manifest: " + a.path("type").asString(""));
            }
            String key = a.path("key").asString("");
            String path = a.path("path").asString("");
            // Le chemin est RECALCULÉ depuis (type, clé) : un chemin du manifeste différent est refusé, jamais suivi.
            BundleEntry probe = new BundleEntry(type, key, new byte[0]);
            if (!path.equals(type.path(probe.key()))) {
                throw new InvalidBundleException("manifest path '" + path + "' does not match " + type + " '" + key + "'");
            }
            byte[] content = files.get(path);
            if (content == null) {
                throw new InvalidBundleException("file listed in the manifest is missing: " + path);
            }
            if (!BundleEntry.sha256(content).equals(a.path("sha256").asString(""))) {
                throw new InvalidBundleException("checksum mismatch for " + path + ": the file was altered");
            }
            if (!declaredPaths.add(path)) {
                throw new InvalidBundleException("artifact listed twice in the manifest: " + path);
            }
            entries.add(new BundleEntry(type, key, content));
        }
        if (!declaredPaths.containsAll(files.keySet())) {
            Set<String> extra = new HashSet<>(files.keySet());
            extra.removeAll(declaredPaths);
            throw new InvalidBundleException("files not declared in the manifest: " + extra);
        }
        Instant generatedAt;
        try {
            generatedAt = Instant.parse(manifest.path("generatedAt").asString(""));
        } catch (RuntimeException e) {
            throw new InvalidBundleException(MANIFEST + " has no valid generatedAt");
        }
        return new ConfigurationBundle(formatVersion, generatedAt, manifest.path("sourceLabel").asString(""),
                manifest.path("appVersion").asString(""), entries);
    }

    private static byte[] readBounded(InputStream in, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > ConfigurationBundle.MAX_ENTRY_BYTES) {
                throw new InvalidBundleException("file " + name + " exceeds " + ConfigurationBundle.MAX_ENTRY_BYTES + " bytes");
            }
        }
        return out.toByteArray();
    }
}
