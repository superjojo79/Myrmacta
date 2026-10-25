package be.myrmacta.domain.reporting;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierPermission;
import be.myrmacta.domain.dossier.model.DossierTypeAclEntry;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SlaReportAccess {

    private SlaReportAccess() {}

    /**
     * {@code null} = unrestricted (TA/WA/system). Empty = no visible types.
     */
    public static Set<String> allowedTypeCodes(
            DossierTypeAclRepository aclRepository, String tenantId, AccessContext ctx) {
        Objects.requireNonNull(aclRepository, "aclRepository");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ctx, "ctx");
        return switch (ctx) {
            case AccessContext.System ignored -> null;
            case AccessContext.Service ignored -> throw new AccessDeniedException(
                    "Service context cannot access SLA reports");
            case AccessContext.External ignored -> throw new AccessDeniedException(
                    "External portal context cannot access SLA reports");
            case AccessContext.User user -> {
                if (user.hasRole("TENANT_ADMIN") || user.hasRole("WORKFLOW_ADMIN") || user.hasRole("API_SERVICE")) {
                    yield null;
                }
                if (!user.hasRole("DOSSIER_MANAGER")) {
                    throw new AccessDeniedException("Access denied for SLA reports");
                }
                yield aclRepository.findBySubject(tenantId, user.userId()).stream()
                        .filter(entry -> entry.permission().implies(DossierPermission.VIEW))
                        .map(DossierTypeAclEntry::dossierTypeCode)
                        .collect(Collectors.toSet());
            }
        };
    }

    public static Set<String> typeFilter(Set<String> allowed, String dossierTypeCode) {
        if (allowed != null && allowed.isEmpty()) {
            return Set.of();
        }
        if (dossierTypeCode != null) {
            if (allowed != null && !allowed.contains(dossierTypeCode)) {
                return Set.of();
            }
            return Set.of(dossierTypeCode);
        }
        return allowed;
    }
}
