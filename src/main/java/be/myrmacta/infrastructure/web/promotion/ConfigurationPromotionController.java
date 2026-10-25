package be.myrmacta.infrastructure.web.promotion;

import be.myrmacta.domain.promotion.model.ConfigImportRecord;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.ImportPlan;
import be.myrmacta.domain.promotion.model.ImportReport;
import be.myrmacta.domain.promotion.model.InvalidBundleException;
import be.myrmacta.domain.promotion.port.in.ExportConfigurationUseCase;
import be.myrmacta.domain.promotion.port.in.ImportConfigurationUseCase;
import be.myrmacta.domain.promotion.port.out.ConfigurationBundleCodec;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §83 (D4) — promotion de configuration : export (ZIP), plan à blanc, application, journal. {@code TENANT_ADMIN} seulement.
 * Les secrets de création de connecteur arrivent dans une partie de formulaire dédiée : jamais journalisés, jamais renvoyés.
 */
@RestController
@RequestMapping("/api/admin/configuration")
@PreAuthorize("hasRole('TENANT_ADMIN')")
class ConfigurationPromotionController {

    private final ExportConfigurationUseCase exportUseCase;
    private final ImportConfigurationUseCase importUseCase;
    private final ConfigurationBundleCodec bundleCodec;
    private final AccessContextFactory accessContextFactory;
    private final TenantProperties tenantProperties;
    private final ObjectMapper objectMapper;

    ConfigurationPromotionController(
            ExportConfigurationUseCase exportUseCase,
            ImportConfigurationUseCase importUseCase,
            ConfigurationBundleCodec bundleCodec,
            AccessContextFactory accessContextFactory,
            TenantProperties tenantProperties,
            ObjectMapper objectMapper) {
        this.exportUseCase = exportUseCase;
        this.importUseCase = importUseCase;
        this.bundleCodec = bundleCodec;
        this.accessContextFactory = accessContextFactory;
        this.tenantProperties = tenantProperties;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/export")
    ResponseEntity<byte[]> export(@RequestParam(required = false) String sourceLabel, Authentication authentication) {
        ConfigurationBundle bundle = exportUseCase.export(accessContextFactory.user(authentication), sourceLabel);
        byte[] archive = bundleCodec.write(bundle);
        String filename = "myrmacta-config-" + tenantProperties.id() + "-" + LocalDate.now(ZoneOffset.UTC) + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header("X-Bundle-Sha256", bundle.sha256())
                .body(archive);
    }

    @PostMapping(value = "/plan", consumes = "multipart/form-data")
    ImportPlan plan(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parameters", required = false) String parameters,
            Authentication authentication) {
        return importUseCase.plan(
                accessContextFactory.user(authentication), readBundle(file), parseStringMap(parameters, "parameters"));
    }

    @PostMapping(value = "/apply", consumes = "multipart/form-data")
    ImportReport apply(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "secrets", required = false) String secrets,
            @RequestParam(value = "parameters", required = false) String parameters,
            @RequestParam(value = "expectedBundleSha256", required = false) String expectedBundleSha256,
            Authentication authentication) {
        return importUseCase.apply(
                accessContextFactory.user(authentication),
                readBundle(file),
                parseSecrets(secrets),
                parseStringMap(parameters, "parameters"),
                expectedBundleSha256,
                ImportConfigurationUseCase.Channel.ADMIN);
    }

    @GetMapping("/imports")
    List<ConfigImportRecord> imports(@RequestParam(required = false, defaultValue = "20") int limit, Authentication authentication) {
        return importUseCase.recentImports(accessContextFactory.user(authentication), limit);
    }

    private ConfigurationBundle readBundle(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidBundleException("file is required");
        }
        try {
            return bundleCodec.read(file.getBytes());
        } catch (IOException e) {
            throw new InvalidBundleException("the bundle could not be read", e);
        }
    }

    private Map<String, String> parseStringMap(String json, String name) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (RuntimeException e) {
            // aucun contenu du champ dans le message
            throw new IllegalArgumentException(name + " must be a JSON object {name: value}");
        }
    }

    private Map<String, Map<String, String>> parseSecrets(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, String>>>() {});
        } catch (RuntimeException e) {
            // jamais d'écho du contenu : ce sont des secrets
            throw new IllegalArgumentException("secrets must be a JSON object {connectorName: {field: value}}");
        }
    }
}
