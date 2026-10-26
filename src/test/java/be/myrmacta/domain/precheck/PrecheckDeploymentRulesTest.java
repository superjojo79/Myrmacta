package be.myrmacta.domain.precheck;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.precheck.model.PrecheckBlock;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §106 (lot B, P8, C3, R5, D-PRECHECK-BLOCK) — contrôles de la brique au déploiement. */
class PrecheckDeploymentRulesTest {

    private final DossierTypeRepository types = mock(DossierTypeRepository.class);
    private boolean enabled = true;
    private final PrecheckDeploymentRules rules = new PrecheckDeploymentRules("t", () -> enabled, types);

    @BeforeEach
    void setUp() {
        when(types.findAll("t")).thenReturn(List.of(
                type("RIV", "riverain"),
                new DossierType(UUID.randomUUID(), "t", "AUTRE", "Autre", "autre", true, false, List.of(), null, false,
                        null, List.of(), List.of(), "u", "u")));
    }

    private static DossierType type(String code, String workflowKey) {
        return new DossierType(UUID.randomUUID(), "t", code, code, workflowKey, true, false,
                List.of(new FieldDefinition("commune", "Commune", FieldType.STRING, false, null, null, null, false),
                        new FieldDefinition("nrn", "NRN", FieldType.STRING, false, null, null, null, true)),
                null, false, null, List.of(),
                List.of(new PieceType("preuve", "Preuve", false), new PieceType("identite", "Identité", true)), "u", "u");
    }

    private static List<PrecheckBlock> block(String... kv) {
        Map<String, String> attributes = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            attributes.put(kv[i], kv[i + 1]);
        }
        return List.of(new PrecheckBlock("check", PrecheckSpec.parse(attributes, "check")));
    }

    @Test
    void aValidBlockIsAccepted() {
        assertThatCode(() -> rules.verify("riverain", block(PrecheckSpec.PIECES, "preuve : obligatoire, lire\n"
                + "identite : obligatoire", PrecheckSpec.FIELDS, "commune"))).doesNotThrowAnyException();
    }

    @Test
    void noBlockNeedsNothing() {
        enabled = false;
        assertThatCode(() -> rules.verify("riverain", List.of())).doesNotThrowAnyException();
        verifyNoInteractions(types);
    }

    @Test
    void aBlockIsRefusedWhileTheFeatureIsDisabled() {
        enabled = false;
        assertThatThrownBy(() -> rules.verify("riverain", block(PrecheckSpec.PIECES, "preuve : lire")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("APP_PRECHECK_ENABLED=true");
    }

    @Test
    void fieldsMustExistAndNotBeSensitive() {
        assertThatThrownBy(() -> rules.verify("riverain", block(PrecheckSpec.PIECES, "preuve : lire",
                PrecheckSpec.FIELDS, "inconnu")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("dossier type RIV")
                .hasMessageContaining("unknown field inconnu");
        assertThatThrownBy(() -> rules.verify("riverain", block(PrecheckSpec.PIECES, "preuve : lire",
                PrecheckSpec.FIELDS, "nrn")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("sensitive field nrn");
    }

    @Test
    void piecesMustExistAndASensitiveOneIsNeverRead() {
        assertThatThrownBy(() -> rules.verify("riverain", block(PrecheckSpec.PIECES, "inconnue : obligatoire")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("unknown piece type inconnue");
        assertThatThrownBy(() -> rules.verify("riverain", block(PrecheckSpec.PIECES, "identite : lire")))
                .isInstanceOf(InvalidBpmnException.class).hasMessageContaining("sensitive piece type identite");
    }

    @Test
    void onlyTypesBoundToTheProcessAreChecked() {
        assertThatCode(() -> rules.verify("autre-processus", block(PrecheckSpec.PIECES, "inconnue : lire")))
                .doesNotThrowAnyException();
    }
}
