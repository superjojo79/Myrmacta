package com.flowforge.domain.connector.usecase;

import com.flowforge.domain.connector.model.InboundAttachment;
import com.flowforge.domain.connector.port.in.AttachInboundFilesUseCase;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.AttachmentContentSniffer;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.AttachmentScanPort;
import com.flowforge.domain.dossier.port.out.AttachmentScanResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * TODO §87 (D3c) — même pipeline que l'upload interne et le portail : nombre et taille bornés, types autorisés, signature du
 * contenu (magic bytes), antivirus, stockage, puis élément {@code ATTACHMENT}. Un fichier refusé est écarté et motivé, jamais
 * rattaché ; les autres suivent (un e-mail ne doit pas perdre sa demande à cause d'une signature en image refusée).
 */
public final class AttachInboundFilesUseCaseImpl implements AttachInboundFilesUseCase {

    private static final int MAX_LABEL_LENGTH = 200;

    private final String tenantId;
    private final Predicate<String> allowedContentType;
    private final int maxFiles;
    private final long maxFileBytes;
    private final AttachmentScanPort scanPort;
    private final DocumentStoragePort storagePort;
    private final AddDossierElementUseCase addElementUseCase;

    public AttachInboundFilesUseCaseImpl(
            String tenantId,
            Predicate<String> allowedContentType,
            int maxFiles,
            long maxFileBytes,
            AttachmentScanPort scanPort,
            DocumentStoragePort storagePort,
            AddDossierElementUseCase addElementUseCase) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.allowedContentType = Objects.requireNonNull(allowedContentType, "allowedContentType");
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileBytes;
        this.scanPort = Objects.requireNonNull(scanPort, "scanPort");
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort");
        this.addElementUseCase = Objects.requireNonNull(addElementUseCase, "addElementUseCase");
    }

    @Override
    public Report attach(UUID dossierId, String addedBy, List<InboundAttachment> files) {
        Objects.requireNonNull(dossierId, "dossierId");
        int attached = 0;
        List<String> rejections = new ArrayList<>();
        for (InboundAttachment file : files) {
            String name = file.filename().isBlank() ? "(sans nom)" : file.filename();
            if (attached + rejections.size() >= maxFiles) {
                rejections.add(name + ": too many attachments (max " + maxFiles + ")");
                continue;
            }
            String rejection = rejection(file);
            if (rejection != null) {
                rejections.add(name + ": " + rejection);
                continue;
            }
            String storageRef = storagePort.store(tenantId, file.contentType(), file.content());
            addElementUseCase.add(
                    new AddDossierElementUseCase.AddCommand(
                            dossierId,
                            ElementType.ATTACHMENT,
                            label(name),
                            storageRef,
                            Map.of("filename", file.filename(), "contentType", file.contentType(), "sizeBytes", (long) file.content().length),
                            addedBy,
                            null),
                    AccessContext.system(addedBy));
            attached++;
        }
        return new Report(attached, rejections);
    }

    private String rejection(InboundAttachment file) {
        if (file.content().length == 0) {
            return "empty file";
        }
        if (file.content().length > maxFileBytes) {
            return "file exceeds " + maxFileBytes + " bytes";
        }
        if (!allowedContentType.test(file.contentType())) {
            return "content type not allowed: " + file.contentType();
        }
        if (!AttachmentContentSniffer.matchesDeclaredType(file.content(), file.contentType())) {
            return "file content does not match declared type: " + file.contentType();
        }
        AttachmentScanResult scan = scanPort.scan(file.content(), file.contentType());
        if (!scan.clean()) {
            return "rejected by the antivirus: " + scan.reason();
        }
        return null;
    }

    private static String label(String name) {
        return name.length() > MAX_LABEL_LENGTH ? name.substring(0, MAX_LABEL_LENGTH) : name;
    }
}
