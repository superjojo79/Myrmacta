package com.flowforge.domain.workflow.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.AllowedBpmnDelegates;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** S-CAT-1, S-CAT-2, S-CAT-3 — catalogue des briques de la palette métier (TODO §73, LC1). */
class BuildingBlockCatalogTest {

    private final List<BuildingBlock> blocks = BuildingBlockCatalog.all();

    @Test
    void containsTheElevenBlocksOfTheFirstSliceWithUniqueIds() {
        assertThat(blocks).extracting(BuildingBlock::id).containsExactly(
                "generate-document", "attach-document", "call-connector", "notify", "create-child-dossier",
                "set-dossier-status", "set-workflow-status", "dmn-decision", "user-question", "external-task",
                "request-signature");
        assertThat(new HashSet<>(blocks.stream().map(BuildingBlock::id).toList())).hasSize(blocks.size());
        assertThat(blocks).allSatisfy(b -> {
            assertThat(b.label()).isNotBlank();
            assertThat(b.description()).isNotBlank();
            assertThat(b.category()).isNotNull();
            assertThat(b.element()).isNotNull();
            assertThat(b.marker()).isNotNull();
        });
    }

    @Test
    void everyDelegateBlockUsesAnAllowlistedDelegate() {
        assertThat(blocks.stream().map(b -> b.marker().delegate()).filter(d -> d != null))
                .allSatisfy(d -> assertThat(AllowedBpmnDelegates.NAMES).contains(d));
        // les sept delegates métier + la signature sont tous couverts par une brique
        Set<String> covered = new HashSet<>();
        blocks.forEach(b -> {
            if (b.marker().delegate() != null) {
                covered.add(b.marker().delegate());
            }
        });
        assertThat(covered).containsExactlyInAnyOrderElementsOf(AllowedBpmnDelegates.NAMES);
    }

    @Test
    void parametersAreWellFormed() {
        for (BuildingBlock block : blocks) {
            Set<String> names = new HashSet<>();
            for (BlockParameter p : block.parameters()) {
                assertThat(names.add(p.name())).as(block.id() + " duplicate " + p.name()).isTrue();
                assertThat(p.label()).isNotBlank();
                assertThat(p.type()).isNotNull();
                assertThat(p.binding()).isNotNull();
                if (p.type() == ParameterType.REFERENCE) {
                    assertThat(p.valueSource()).as(block.id() + "." + p.name()).isNotBlank();
                    assertThat(BuildingBlockCatalog.VALUE_SOURCES).contains(p.valueSource());
                }
                if (p.type() == ParameterType.ENUM || p.type() == ParameterType.ENUM_MULTI) {
                    assertThat(p.enumValues()).as(block.id() + "." + p.name()).isNotEmpty();
                }
                if (p.type() == ParameterType.INTEGER) {
                    assertThat(p.min()).isNotNull();
                    assertThat(p.max()).isNotNull();
                }
                assertThat(p.maxLength()).isPositive();
            }
        }
    }

    @Test
    void requiredParametersMatchTheDelegatesRequiredVariables() {
        assertThat(required("generate-document")).containsExactly("documentType");
        assertThat(required("call-connector")).containsExactly("connectorInstanceId");
        assertThat(required("notify")).containsExactly("notificationChannels");
        assertThat(required("create-child-dossier"))
                .containsExactly("childDossierTypeCode", "childDossierLabel", "workflowProcessKey", "relationType");
        assertThat(required("set-dossier-status")).containsExactly("targetDossierStatus");
        assertThat(required("set-workflow-status")).containsExactly("targetWorkflowStatus");
        assertThat(required("dmn-decision")).containsExactly("decisionTableReferenceKey");
        assertThat(required("user-question")).containsExactly("schemaKey");
        assertThat(required("external-task")).containsExactly("portalRecipient", "schemaKey");
        assertThat(required("request-signature")).containsExactly("signatureDocument", "signers");
        assertThat(required("attach-document")).isEmpty();
    }

    @Test
    void newAttributeBlocksBindAsPlainFlowableAttributes() {
        for (String id : List.of("generate-document", "call-connector", "notify", "create-child-dossier",
                "set-dossier-status", "set-workflow-status")) {
            assertThat(block(id).parameters()).allSatisfy(p -> assertThat(p.binding()).isEqualTo(ParameterBinding.ATTRIBUTE));
        }
        assertThat(param("dmn-decision", "decisionTableReferenceKey").binding()).isEqualTo(ParameterBinding.FIELD_ELEMENT);
        assertThat(param("user-question", "schemaKey").binding()).isEqualTo(ParameterBinding.FORM_KEY_SUFFIX);
        assertThat(param("request-signature", "signers").binding()).isEqualTo(ParameterBinding.SIGNER_ELEMENTS);
    }

    @Test
    void enumeratedValuesComeFromTheDomainEnums() {
        assertThat(param("set-dossier-status", "targetDossierStatus").enumValues())
                .containsExactly("DRAFT", "OUVERT", "EN_COURS", "SUSPENDU", "CLOTURE", "ARCHIVE");
        assertThat(param("notify", "notificationChannels").enumValues())
                .containsExactly("EMAIL", "WEBHOOK", "BROWSER");
        assertThat(param("create-child-dossier", "relationType").enumValues())
                .containsExactly("PARENT_CHILD", "SUBSEQUENT");
        assertThat(param("request-signature", "signatureMode").enumValues()).containsExactly("PARALLEL", "SEQUENTIAL");
    }

    @Test
    void catalogCarriesNoTenantData() {
        // sources nommées seulement : aucun paramètre ne porte de valeur concrète (S-CAT-3)
        for (BuildingBlock block : blocks) {
            for (BlockParameter p : block.parameters()) {
                assertThat(p.enumValues()).allSatisfy(v -> assertThat(v).doesNotContain(" "));
            }
        }
        assertThat(BuildingBlockCatalog.VALUE_SOURCES).containsExactlyInAnyOrder(
                "documentTemplates", "connectorInstances", "notificationWebhooks", "dossierTypes", "workflowTypes",
                "workflowStatuses", "dmnTables", "questionSchemas");
    }

    @Test
    void lookupsByIdAndDelegate() {
        assertThat(BuildingBlockCatalog.byId("notify")).isPresent();
        assertThat(BuildingBlockCatalog.byId("nope")).isEmpty();
        assertThat(BuildingBlockCatalog.forDelegate("updateDossierStatusTask")).map(BuildingBlock::id)
                .contains("set-dossier-status");
        assertThat(BuildingBlockCatalog.forDelegate("attachGeneratedDocumentTask")).map(BuildingBlock::id)
                .contains("attach-document");
        assertThat(BuildingBlockCatalog.forDelegate("unknown")).isEmpty();
        assertThat(BuildingBlockCatalog.VERSION).isPositive();
    }

    @Test
    void blockRejectsInvalidDefinitions() {
        assertThatThrownBy(() -> new BlockParameter("a", "A", ParameterType.REFERENCE, ParameterBinding.ATTRIBUTE,
                false, null, List.of(), 10, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockParameter("a", "A", ParameterType.ENUM, ParameterBinding.ATTRIBUTE,
                false, null, List.of(), 10, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private List<String> required(String id) {
        return block(id).parameters().stream().filter(BlockParameter::required).map(BlockParameter::name).toList();
    }

    private BuildingBlock block(String id) {
        return BuildingBlockCatalog.byId(id).orElseThrow();
    }

    private BlockParameter param(String blockId, String name) {
        return block(blockId).parameters().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }
}
