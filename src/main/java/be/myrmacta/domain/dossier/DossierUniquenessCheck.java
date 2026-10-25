package be.myrmacta.domain.dossier;

import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldValidationError;
import be.myrmacta.domain.dossier.model.UniqueFieldKeys;
import be.myrmacta.domain.dossier.model.UniqueKey;
import be.myrmacta.domain.dossier.port.out.DossierUniquenessPort;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TODO §102 (issue #184, U8) — pré-contrôle d'unicité des use cases d'écriture : une erreur {@code UNIQUE} lisible, avec la
 * référence du dossier détenteur, rendue avec les autres erreurs de champ. La garantie reste tenue à l'enregistrement
 * (D-UNIQUE-STORE) : ce contrôle n'est qu'une politesse, sans verrou.
 */
public final class DossierUniquenessCheck {

    private static final DossierUniquenessCheck DISABLED = new DossierUniquenessCheck((tenant, type, keys, exclude) -> Map.of());

    private final DossierUniquenessPort port;

    public DossierUniquenessCheck(DossierUniquenessPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    /** Sans lecture : pour les constructeurs historiques des use cases (tests unitaires sans base). */
    public static DossierUniquenessCheck disabled() {
        return DISABLED;
    }

    /**
     * @param fieldsInError champs déjà en erreur : leur valeur n'est pas fiable, on ne la contrôle pas
     * @param self dossier en cours d'écriture, {@code null} pour une création
     */
    public List<FieldValidationError> conflicts(
            DossierType type,
            DossierStatus status,
            Map<String, Object> fields,
            Collection<String> fieldsInError,
            UUID self) {
        Set<UniqueKey> keys = UniqueFieldKeys.of(type.fieldDefinitions(), status, fields).stream()
                .filter(key -> !fieldsInError.contains(key.field()))
                .collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return List.of();
        }
        return port.findHolders(type.tenantId(), type.code(), keys, self).entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().field()))
                .map(entry -> FieldValidationError.notUnique(entry.getKey().field(), entry.getValue()))
                .toList();
    }
}
