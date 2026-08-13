package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.DossierPage;
import com.flowforge.domain.dossier.model.DossierStatus;
import java.util.Objects;
import java.util.Set;

public interface DossierSearchPort {

    DossierPage search(String tenantId, Filter filter, int page, int size);

    /**
     * @param dossierTypeCodes {@code null} = no type restriction (admin bypass);
     *     non-null set = restrict to those codes (must be non-empty when called).
     */
    record Filter(DossierStatus status, Set<String> dossierTypeCodes, String referencePrefix) {
        public Filter {
            if (dossierTypeCodes != null) {
                dossierTypeCodes = Set.copyOf(dossierTypeCodes);
            }
            if (referencePrefix != null && referencePrefix.isBlank()) {
                referencePrefix = null;
            }
        }

        public static Filter unrestricted(DossierStatus status, String referencePrefix) {
            return new Filter(status, null, referencePrefix);
        }

        public static Filter restricted(
                DossierStatus status, Set<String> dossierTypeCodes, String referencePrefix) {
            Objects.requireNonNull(dossierTypeCodes, "dossierTypeCodes");
            return new Filter(status, dossierTypeCodes, referencePrefix);
        }
    }
}
