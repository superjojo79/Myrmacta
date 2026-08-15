package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class DossierApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AddDossierElementUseCase addDossierElementUseCase;

    @Test
    void createDossier_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/dossiers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "CLAIM",
                                  "label": "Claim A",
                                  "fields": {}
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createType_tenantAdmin_returns201() throws Exception {
        String code = uniqueCode("TYPE");

        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-create-type", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.label").value("Label " + code))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void updateType_tenantAdminCanPatchLabelAndActive() throws Exception {
        String code = createType(true);

        mockMvc.perform(patch("/api/dossier-types/{code}", code)
                        .with(user("admin-patch-type", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "Patched %s",
                                  "active": false
                                }
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.label").value("Patched " + code))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void createType_workflowAdmin_returns403ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("workflow-admin", "WORKFLOW_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(uniqueCode("DENIED"), true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void createType_dossierManager_returns403ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("manager-denied", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(uniqueCode("MGRDENY"), true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void createType_withSensitiveField_roundTripsFlag() throws Exception {
        String code = uniqueCode("SENS");
        String fields = """
                [{
                  "name": "ssn",
                  "label": "SSN",
                  "type": "STRING",
                  "required": false,
                  "options": [],
                  "sensitive": true
                }]
                """;

        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-sens", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true, fields)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fieldDefinitions[0].sensitive").value(true));

        mockMvc.perform(get("/api/dossier-types/{code}", code)
                        .with(user("admin-sens", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldDefinitions[0].name").value("ssn"))
                .andExpect(jsonPath("$.fieldDefinitions[0].sensitive").value(true));
    }

    @Test
    void createType_withoutSensitiveKey_defaultsToFalse() throws Exception {
        String code = uniqueCode("NOSENS");
        String fields = """
                [{
                  "name": "city",
                  "label": "City",
                  "type": "STRING",
                  "required": false,
                  "options": []
                }]
                """;

        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-nosens", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true, fields)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fieldDefinitions[0].sensitive").value(false));
    }

    @Test
    void getDossier_sensitiveFieldValue_redactedForNonAdminButClearForTenantAdmin() throws Exception {
        String code = uniqueCode("SSNTYPE");
        String fields = """
                [{
                  "name": "ssn",
                  "label": "SSN",
                  "type": "STRING",
                  "required": true,
                  "options": [],
                  "sensitive": true
                }]
                """;
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-ssn", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true, fields)))
                .andExpect(status().isCreated());
        grant(code, "manager-ssn", "EDIT");

        MvcResult created = mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-ssn"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Sensitive dossier", "{\"ssn\":\"123-45-6789\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields.ssn").value("123-45-6789"))
                .andReturn();
        String id = extractString(created.getResponse().getContentAsString(), "id");

        // D-SENSITIVE = A: masked server-side for every viewer except TENANT_ADMIN, even one
        // holding an EDIT ACL entry on the dossier type.
        mockMvc.perform(get("/api/dossiers/{id}", id).with(user("manager-ssn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.ssn").doesNotExist());

        mockMvc.perform(get("/api/dossiers/{id}", id).with(user("admin-ssn", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.ssn").value("123-45-6789"));
    }

    @Test
    void createDossier_managerWithEditAcl_returns201Draft() throws Exception {
        String code = createType(true);
        grant(code, "manager-create", "EDIT");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-create"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Manager dossier")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.dossierTypeCode").value(code))
                .andExpect(jsonPath("$.label").value("Manager dossier"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value("manager-create"))
                .andExpect(jsonPath("$.updatedBy").value("manager-create"));
    }

    @Test
    void createDossier_appliesFieldDefaultWhenFieldIsMissing() throws Exception {
        String code = createTypeWithFields("DEFAULT", """
                [
                  {
                    "name": "priority",
                    "label": "Priority",
                    "type": "STRING",
                    "required": false,
                    "defaultValue": "NORMAL",
                    "options": [],
                    "group": "details"
                  }
                ]
                """);
        grant(code, "manager-default", "EDIT");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-default"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Default dossier")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fields.priority").value("NORMAL"));
    }

    @Test
    void createDossier_withInvalidFieldTypeReturns400WithErrors() throws Exception {
        String code = createTypeWithFields("INVALID", """
                [
                  {
                    "name": "amount",
                    "label": "Amount",
                    "type": "NUMBER",
                    "required": true,
                    "defaultValue": null,
                    "options": [],
                    "group": "details"
                  }
                ]
                """);
        grant(code, "manager-invalid-field", "EDIT");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-invalid-field"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Invalid field dossier", """
                                {
                                  "amount": "not-a-number"
                                }
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"));
    }

    @Test
    void createDossier_withoutLabel_returns400() throws Exception {
        String code = createType(true);
        grant(code, "manager-missing-label", "EDIT");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-missing-label"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dossierTypeCode": "%s",
                                  "fields": {}
                                }
                                """.formatted(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createDossier_inactiveType_returns409() throws Exception {
        String code = createType(false);
        grant(code, "manager-inactive", "EDIT");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("manager-inactive"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Inactive type dossier")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void getDossier_withoutViewAcl_returns403() throws Exception {
        String code = createType(true);
        grant(code, "manager-owner", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-owner", "Owner dossier");

        mockMvc.perform(get("/api/dossiers/{id}", dossier.id())
                        .with(user("no-view-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDossier_withViewGrant_returns200() throws Exception {
        String code = createType(true);
        grant(code, "manager-view-source", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-view-source", "Viewable dossier");
        grant(code, "viewer-with-grant", "VIEW");

        mockMvc.perform(get("/api/dossiers/{id}", dossier.id())
                        .with(user("viewer-with-grant", "DOSSIER_VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dossier.id()))
                .andExpect(jsonPath("$.dossierTypeCode").value(code));
    }

    @Test
    void getDossier_workflowAdminWithoutGrant_returns200() throws Exception {
        String code = createType(true);
        grant(code, "manager-workflow-bypass-source", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-workflow-bypass-source", "Workflow bypass dossier");

        mockMvc.perform(get("/api/dossiers/{id}", dossier.id())
                        .with(user("workflow-bypass", "WORKFLOW_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dossier.id()));
    }

    @Test
    void revokeAcl_thenCreateAndGetAreDenied() throws Exception {
        String code = createType(true);
        grant(code, "revoked-manager", "EDIT");
        DossierFixture dossier = createDossier(code, "revoked-manager", "Revoked dossier");

        revoke(code, "revoked-manager");

        mockMvc.perform(post("/api/dossiers")
                        .with(user("revoked-manager"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, "Denied after revoke")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dossiers/{id}", dossier.id())
                        .with(user("revoked-manager")))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantAcl_twiceIsIdempotent() throws Exception {
        String code = createType(true);

        grant(code, "idempotent-manager", "EDIT");
        grant(code, "idempotent-manager", "EDIT");
    }

    @Test
    void grantAcl_dossierManager_returns403ProblemDetail() throws Exception {
        String code = createType(true);

        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("manager-acl", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectUserId": "someone",
                                  "permission": "VIEW"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void updateStatus_viewerWithViewOnly_returns403() throws Exception {
        String code = createType(true);
        grant(code, "setup-editor", "EDIT");
        grant(code, "viewer-status", "VIEW");
        DossierFixture dossier = createDossier(code, "setup-editor", "Viewer status dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/status", dossier.id())
                        .with(user("viewer-status", "DOSSIER_VIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "OUVERT"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void updateStatus_legalTransition_returnsUpdatedDossier() throws Exception {
        String code = createType(true);
        grant(code, "manager-status", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-status", "Status dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/status", dossier.id())
                        .with(user("manager-status"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "OUVERT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dossier.id()))
                .andExpect(jsonPath("$.status").value("OUVERT"))
                .andExpect(jsonPath("$.updatedBy").value("manager-status"))
                .andExpect(jsonPath("$.version").value(dossier.version() + 1));
    }

    @Test
    void createType_duplicateCode_returns409() throws Exception {
        String code = createType(true);

        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-dup", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Unique constraint or data integrity violation"));
    }

    @Test
    void grantAcl_unknownType_returns404() throws Exception {
        mockMvc.perform(post("/api/dossier-types/{code}/acl", "MISSING_TYPE")
                        .with(user("acl-admin-missing", "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectUserId": "someone",
                                  "permission": "VIEW"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDossier_byReference_returns200() throws Exception {
        String code = createType(true);
        grant(code, "manager-ref", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-ref", "Ref dossier");

        mockMvc.perform(get("/api/dossiers").param("ref", dossier.reference())
                        .with(user("manager-ref")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dossier.id()))
                .andExpect(jsonPath("$.reference").value(dossier.reference()));
    }

    @Test
    void updateStatus_staleExpectedVersion_returns409() throws Exception {
        String code = createType(true);
        grant(code, "manager-version", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-version", "Version dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/status", dossier.id())
                        .with(user("manager-version"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "OUVERT",
                                  "expectedVersion": %d
                                }
                                """.formatted(dossier.version() + 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateStatus_illegalTransition_returns409() throws Exception {
        String code = createType(true);
        grant(code, "manager-illegal", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-illegal", "Illegal transition");

        mockMvc.perform(patch("/api/dossiers/{id}/status", dossier.id())
                        .with(user("manager-illegal"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ARCHIVE"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateFields_managerWithEdit_mergesAndReturns200() throws Exception {
        String code = createTypeWithFields("PATCHFIELDS", """
                [
                  {"name": "montant", "label": "Montant", "type": "NUMBER", "required": false, "options": [], "group": "d"},
                  {"name": "commentaire", "label": "Commentaire", "type": "STRING", "required": false, "options": [], "group": "d"}
                ]
                """);
        grant(code, "manager-patch-fields", "EDIT");
        DossierFixture dossier = createDossierWithFields(
                code, "manager-patch-fields", "Patch fields dossier", "{\"montant\":100,\"commentaire\":\"old\"}");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-fields"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"montant": 200}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dossier.id()))
                .andExpect(jsonPath("$.fields.montant").value(200))
                .andExpect(jsonPath("$.fields.commentaire").value("old"))
                .andExpect(jsonPath("$.updatedBy").value("manager-patch-fields"))
                .andExpect(jsonPath("$.version").value(dossier.version() + 1));
    }

    @Test
    void updateFields_nullValueClearsField() throws Exception {
        String code = createTypeWithFields("PATCHCLEAR", """
                [{"name": "commentaire", "label": "Commentaire", "type": "STRING", "required": false, "options": [], "group": "d"}]
                """);
        grant(code, "manager-patch-clear", "EDIT");
        DossierFixture dossier = createDossierWithFields(
                code, "manager-patch-clear", "Clear field dossier", "{\"commentaire\":\"old\"}");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-clear"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"commentaire": null}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields").value(not(hasKey("commentaire"))));
    }

    @Test
    void updateFields_emptyFields_returns400() throws Exception {
        String code = createType(true);
        grant(code, "manager-patch-empty", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-patch-empty", "Empty patch dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-empty"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateFields_viewerWithViewOnly_returns403() throws Exception {
        String code = createType(true);
        grant(code, "setup-editor-fields", "EDIT");
        grant(code, "viewer-fields", "VIEW");
        DossierFixture dossier = createDossier(code, "setup-editor-fields", "Viewer fields dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("viewer-fields", "DOSSIER_VIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"montant": 1}
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void updateFields_unknownDossier_returns404() throws Exception {
        mockMvc.perform(patch("/api/dossiers/{id}/fields", UUID.randomUUID())
                        .with(user("manager-patch-404"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"montant": 1}
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateFields_statusCloture_returns409() throws Exception {
        String code = createType(true);
        grant(code, "manager-patch-cloture", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-patch-cloture", "Cloture fields dossier");
        transitionStatus(dossier.id(), "manager-patch-cloture", "OUVERT");
        transitionStatus(dossier.id(), "manager-patch-cloture", "CLOTURE");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-cloture"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"montant": 1}
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateFields_statusClotureWithMatchingExpectedVersion_stillReturns409WithStatusDetail() throws Exception {
        // The status gate must be checked independently of the optimistic lock: even when
        // expectedVersion matches exactly, a CLOTURE dossier is still rejected, and the
        // ProblemDetail must name the status cause (not "Expected version") so a client can
        // tell the two distinct 409 causes apart.
        String code = createType(true);
        grant(code, "manager-patch-cloture-version", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-patch-cloture-version", "Cloture+version dossier");
        transitionStatus(dossier.id(), "manager-patch-cloture-version", "OUVERT");
        transitionStatus(dossier.id(), "manager-patch-cloture-version", "CLOTURE");
        MvcResult afterTransitions = mockMvc.perform(get("/api/dossiers/{id}", dossier.id())
                        .with(user("manager-patch-cloture-version")))
                .andExpect(status().isOk())
                .andReturn();
        long currentVersion = extractLong(afterTransitions.getResponse().getContentAsString(), "version");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-cloture-version"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"montant": 1},
                                  "expectedVersion": %d
                                }
                                """.formatted(currentVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("status")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Expected version"))));
    }

    @Test
    void updateFields_noNetChange_stillAdvancesVersionAndUpdatedAt() throws Exception {
        // D-NOOP-VERSION: a PATCH that merges to the exact same field values still advances
        // version/updatedAt — no no-op detection exists (or is meant to exist), it's a free
        // side effect of Hibernate's @Version on every save(). End-to-end proof with the real
        // H2 DB, not just a mocked repository.
        String code = createTypeWithFields("PATCHNOOP", """
                [{"name": "montant", "label": "Montant", "type": "NUMBER", "required": false, "options": [], "group": "d"}]
                """);
        grant(code, "manager-patch-noop", "EDIT");
        DossierFixture dossier = createDossierWithFields(
                code, "manager-patch-noop", "No-op patch dossier", "{\"montant\":100}");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-noop"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"montant": 100}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.montant").value(100))
                .andExpect(jsonPath("$.version").value(dossier.version() + 1));
    }

    @Test
    void updateFields_multipleSensitiveFieldsChanged_emitsOneSecurityAuditEventPerField() throws Exception {
        String code = createTypeWithFields("PATCHMULTISENS", """
                [
                  {"name": "ssn", "label": "SSN", "type": "STRING", "required": false, "options": [], "sensitive": true},
                  {"name": "iban", "label": "IBAN", "type": "STRING", "required": false, "options": [], "sensitive": true}
                ]
                """);
        grant(code, "manager-patch-multisens", "EDIT");
        DossierFixture dossier = createDossierWithFields(
                code, "manager-patch-multisens", "Multi sensitive dossier",
                "{\"ssn\":\"111-11-1111\",\"iban\":\"FR001\"}");

        ListAppender<ILoggingEvent> appender = attachSecurityAuditAppender();
        try {
            mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                            .with(user("manager-patch-multisens"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "fields": {"ssn": "222-22-2222", "iban": "FR002"}
                                    }
                                    """))
                    .andExpect(status().isOk());
        } finally {
            detachSecurityAuditAppender(appender);
        }

        List<String> sensitiveFieldChangedMessages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(msg -> msg.contains("event=sensitive_field_changed"))
                .toList();
        assertThat(sensitiveFieldChangedMessages)
                .hasSize(2)
                .anyMatch(msg -> msg.contains("field=ssn"))
                .anyMatch(msg -> msg.contains("field=iban"));
    }

    @Test
    void updateFields_statusArchive_returns409() throws Exception {
        String code = createType(true);
        grant(code, "manager-patch-archive", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-patch-archive", "Archive fields dossier");
        transitionStatus(dossier.id(), "manager-patch-archive", "OUVERT");
        transitionStatus(dossier.id(), "manager-patch-archive", "CLOTURE");
        transitionStatus(dossier.id(), "manager-patch-archive", "ARCHIVE");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-archive"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"montant": 1}
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateFields_staleExpectedVersion_returns409() throws Exception {
        String code = createType(true);
        grant(code, "manager-patch-version", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-patch-version", "Version fields dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-version"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"montant": 1},
                                  "expectedVersion": %d
                                }
                                """.formatted(dossier.version() + 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateFields_clearingRequiredField_returns400() throws Exception {
        String code = createTypeWithFields("PATCHREQ", """
                [{"name": "montant", "label": "Montant", "type": "NUMBER", "required": true, "options": [], "group": "d"}]
                """);
        grant(code, "manager-patch-required", "EDIT");
        DossierFixture dossier = createDossierWithFields(
                code, "manager-patch-required", "Required field dossier", "{\"montant\":100}");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-required"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"montant": null}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("montant"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void updateFields_typeMismatch_returns400() throws Exception {
        String code = createTypeWithFields("PATCHTYPE", """
                [{"name": "montant", "label": "Montant", "type": "NUMBER", "required": false, "options": [], "group": "d"}]
                """);
        grant(code, "manager-patch-type", "EDIT");
        DossierFixture dossier = createDossierWithFields(
                code, "manager-patch-type", "Type mismatch dossier", "{\"montant\":100}");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-type"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"montant": "not-a-number"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("montant"))
                .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"));
    }

    @Test
    void updateFields_unknownField_returns400WhenNotAllowed() throws Exception {
        String code = createType(true);
        grant(code, "manager-patch-unknown", "EDIT");
        DossierFixture dossier = createDossier(code, "manager-patch-unknown", "Unknown field dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-unknown"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"mystere": "x"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("mystere"))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
    }

    @Test
    void updateFields_sensitiveFieldChanged_emitsSecurityAuditEvent() throws Exception {
        String code = createTypeWithFields("PATCHSENS", """
                [{"name": "ssn", "label": "SSN", "type": "STRING", "required": false, "options": [], "sensitive": true}]
                """);
        grant(code, "manager-patch-sensitive", "EDIT");
        DossierFixture dossier = createDossierWithFields(
                code, "manager-patch-sensitive", "Sensitive field dossier", "{\"ssn\":\"111-11-1111\"}");

        ListAppender<ILoggingEvent> appender = attachSecurityAuditAppender();
        try {
            mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                            .with(user("manager-patch-sensitive"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "fields": {"ssn": "222-22-2222"}
                                    }
                                    """))
                    .andExpect(status().isOk());
        } finally {
            detachSecurityAuditAppender(appender);
        }

        assertThat(appender.list).anySatisfy(event -> {
            String message = event.getFormattedMessage();
            assertThat(message).contains("event=sensitive_field_changed");
            assertThat(message).contains("field=ssn");
            assertThat(message).doesNotContain("111-11-1111");
            assertThat(message).doesNotContain("222-22-2222");
        });
    }

    @Test
    void updateFields_noSensitiveFieldChanged_emitsNoSecurityAuditEvent() throws Exception {
        String code = createTypeWithFields("PATCHNOSENS", """
                [
                  {"name": "montant", "label": "Montant", "type": "NUMBER", "required": false, "options": [], "group": "d"},
                  {"name": "ssn", "label": "SSN", "type": "STRING", "required": false, "options": [], "sensitive": true}
                ]
                """);
        grant(code, "manager-patch-nosensitive", "EDIT");
        DossierFixture dossier = createDossierWithFields(
                code, "manager-patch-nosensitive", "No sensitive change dossier",
                "{\"montant\":100,\"ssn\":\"111-11-1111\"}");

        ListAppender<ILoggingEvent> appender = attachSecurityAuditAppender();
        try {
            mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                            .with(user("manager-patch-nosensitive"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "fields": {"montant": 200}
                                    }
                                    """))
                    .andExpect(status().isOk());
        } finally {
            detachSecurityAuditAppender(appender);
        }

        assertThat(appender.list)
                .noneMatch(event -> event.getFormattedMessage().contains("event=sensitive_field_changed"));
    }

    @Test
    void updateFields_sensitiveFieldStillRedactedForNonAdminAfterPatch() throws Exception {
        String code = createTypeWithFields("PATCHREDACT", """
                [{"name": "ssn", "label": "SSN", "type": "STRING", "required": false, "options": [], "sensitive": true}]
                """);
        grant(code, "manager-patch-redact", "EDIT");
        DossierFixture dossier = createDossierWithFields(
                code, "manager-patch-redact", "Redact after patch dossier", "{\"ssn\":\"111-11-1111\"}");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", dossier.id())
                        .with(user("manager-patch-redact"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"ssn": "222-22-2222"}
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dossiers/{id}", dossier.id()).with(user("manager-patch-redact")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.ssn").doesNotExist());
    }

    @Test
    void updateFields_thenListSortedByUpdatedAtDesc_bringsEditedDossierToTop() throws Exception {
        String code = createTypeWithFields("PATCHSORT", """
                [{"name": "note", "label": "Note", "type": "STRING", "required": false, "options": [], "group": "d"}]
                """);
        grant(code, "manager-patch-sort", "EDIT");
        DossierFixture older = createDossier(code, "manager-patch-sort", "Older dossier");
        DossierFixture newer = createDossier(code, "manager-patch-sort", "Newer dossier");

        mockMvc.perform(patch("/api/dossiers/{id}/fields", older.id())
                        .with(user("manager-patch-sort"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fields": {"note": "bumped"}
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dossiers")
                        .param("sort", "updatedAt,desc")
                        .param("dossierTypeCode", code)
                        .with(user("manager-patch-sort")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(older.id()));
    }

    private void transitionStatus(String dossierId, String userId, String status) throws Exception {
        mockMvc.perform(patch("/api/dossiers/{id}/status", dossierId)
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "%s"
                                }
                                """.formatted(status)))
                .andExpect(status().isOk());
    }

    private DossierFixture createDossierWithFields(String code, String userId, String label, String fields)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/dossiers")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, label, fields)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new DossierFixture(
                extractString(body, "id"),
                extractString(body, "reference"),
                extractLong(body, "version"));
    }

    private static ListAppender<ILoggingEvent> attachSecurityAuditAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachSecurityAuditAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void createType_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-no-csrf", "TENANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(uniqueCode("CSRF"), true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listElements_returnsViewsWithoutStorageRef() throws Exception {
        String code = createType(true);
        grant(code, "mgr-elements", "EDIT");
        DossierFixture dossier = createDossier(code, "mgr-elements", "Elements dossier");
        addDossierElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        UUID.fromString(dossier.id()),
                        ElementType.PRODUCED_DOC,
                        "Rapport PDF",
                        TENANT_ID + "/secret-path/doc.pdf",
                        Map.of("templateKey", "rapport"),
                        "system",
                        "step-1"),
                AccessContext.system("test"));

        mockMvc.perform(get("/api/dossiers/{id}/elements", dossier.id())
                        .with(user("mgr-elements")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Rapport PDF"))
                .andExpect(jsonPath("$[0].downloadable").value(true))
                .andExpect(jsonPath("$[0].type").value("PRODUCED_DOC"))
                .andExpect(jsonPath("$[0]", not(hasKey("storageRef"))));
    }

    @Test
    void listDossiers_admin_returnsEnvelopeWithTypeLabelAndNoFields() throws Exception {
        String code = createType(true);
        grant(code, "mgr-list", "EDIT");
        DossierFixture dossier = createDossier(code, "mgr-list", "Listed dossier");

        mockMvc.perform(get("/api/dossiers")
                        .param("dossierTypeCode", code)
                        .with(user("admin-list", "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[?(@.id=='%s')].label".formatted(dossier.id())).isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id=='%s')].dossierTypeLabel".formatted(dossier.id())).isNotEmpty())
                .andExpect(jsonPath("$.content[0]", not(hasKey("fields"))));
    }

    @Test
    void listDossiers_userWithoutAcl_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/dossiers").with(user("no-acl-user", "DOSSIER_VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listDossiers_typeOutsideAcl_returnsEmptyPage() throws Exception {
        String visible = createType(true);
        String hidden = createType(true);
        grant(visible, "mgr-filter", "VIEW");
        grant(hidden, "other-user", "EDIT");
        createDossier(hidden, "other-user", "Hidden");

        mockMvc.perform(get("/api/dossiers")
                        .param("dossierTypeCode", hidden)
                        .with(user("mgr-filter", "DOSSIER_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listDossiers_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/dossiers")
                        .param("status", "NOPE")
                        .with(user("admin-bad-status", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listDossiers_invalidPageOrSizeOrSort_returns400() throws Exception {
        mockMvc.perform(get("/api/dossiers")
                        .param("page", "-1")
                        .with(user("admin-bad-page", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dossiers")
                        .param("size", "0")
                        .with(user("admin-bad-size", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dossiers")
                        .param("size", "101")
                        .with(user("admin-bad-size2", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dossiers")
                        .param("sort", "reference,asc")
                        .with(user("admin-bad-sort", "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listDossiers_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/dossiers")).andExpect(status().isUnauthorized());
    }

    @Test
    void listDossiers_refParamStillReturnsSingleDossier() throws Exception {
        String code = createType(true);
        grant(code, "mgr-ref-list", "EDIT");
        DossierFixture dossier = createDossier(code, "mgr-ref-list", "Ref still works");

        mockMvc.perform(get("/api/dossiers")
                        .param("ref", dossier.reference())
                        .param("page", "0")
                        .with(user("mgr-ref-list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dossier.id()))
                .andExpect(jsonPath("$.reference").value(dossier.reference()))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void createAndListRelations_bidirectionalAclAndDuplicate409() throws Exception {
        String sourceType = createType(true);
        String targetType = createType(true);
        grant(sourceType, "rel-editor", "EDIT");
        grant(targetType, "rel-editor", "VIEW");
        grant(sourceType, "rel-viewer", "VIEW");
        DossierFixture source = createDossier(sourceType, "rel-editor", "Source dossier");
        DossierFixture target = createDossier(sourceType, "rel-editor", "Target dossier");

        mockMvc.perform(post("/api/dossiers/{id}/relations", source.id())
                        .with(user("rel-editor"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetDossierId": "%s",
                                  "type": "PARENT_CHILD"
                                }
                                """.formatted(target.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PARENT_CHILD"))
                .andExpect(jsonPath("$.direction").value("OUTGOING"))
                .andExpect(jsonPath("$.relatedDossier.id").value(target.id()))
                .andExpect(jsonPath("$.relatedDossier", not(hasKey("fields"))));

        mockMvc.perform(get("/api/dossiers/{id}/relations", target.id())
                        .with(user("rel-editor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$[0].relatedDossier.id").value(source.id()));

        mockMvc.perform(post("/api/dossiers/{id}/relations", source.id())
                        .with(user("rel-editor"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetDossierId": "%s",
                                  "type": "PARENT_CHILD"
                                }
                                """.formatted(target.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/dossiers/{id}/relations", source.id())
                        .with(user("rel-viewer"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetDossierId": "%s",
                                  "type": "LINKED"
                                }
                                """.formatted(target.id())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/dossiers/{id}/elements", source.id())
                        .with(user("rel-editor")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dossiers")
                        .param("reference", "DOS")
                        .with(user("rel-editor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void createRelation_withoutViewOnTarget_returns403() throws Exception {
        String sourceType = createType(true);
        String hiddenType = createType(true);
        grant(sourceType, "rel-partial", "EDIT");
        grant(hiddenType, "other-owner", "EDIT");
        DossierFixture source = createDossier(sourceType, "rel-partial", "Editable source");
        DossierFixture hidden = createDossier(hiddenType, "other-owner", "Hidden target");

        mockMvc.perform(post("/api/dossiers/{id}/relations", source.id())
                        .with(user("rel-partial"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetDossierId": "%s",
                                  "type": "LINKED"
                                }
                                """.formatted(hidden.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRelation_selfLink_returns400() throws Exception {
        String code = createType(true);
        grant(code, "rel-self", "EDIT");
        DossierFixture dossier = createDossier(code, "rel-self", "Self");

        mockMvc.perform(post("/api/dossiers/{id}/relations", dossier.id())
                        .with(user("rel-self"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetDossierId": "%s",
                                  "type": "LINKED"
                                }
                                """.formatted(dossier.id())))
                .andExpect(status().isBadRequest());
    }

    private String createType(boolean active) throws Exception {
        String code = uniqueCode(active ? "ACTIVE" : "INACTIVE");
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, active)))
                .andExpect(status().isCreated());
        return code;
    }

    private String createTypeWithFields(String prefix, String fieldDefinitions) throws Exception {
        String code = uniqueCode(prefix);
        mockMvc.perform(post("/api/dossier-types")
                        .with(user("admin-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typePayload(code, true, fieldDefinitions)))
                .andExpect(status().isCreated());
        return code;
    }

    private void grant(String code, String userId, String permission) throws Exception {
        mockMvc.perform(post("/api/dossier-types/{code}/acl", code)
                        .with(user("acl-admin-" + code, "TENANT_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectUserId": "%s",
                                  "permission": "%s"
                                }
                                """.formatted(userId, permission)))
                .andExpect(status().isNoContent());
    }

    private void revoke(String code, String userId) throws Exception {
        mockMvc.perform(delete("/api/dossier-types/{code}/acl/{userId}", code, userId)
                        .with(user("acl-admin-" + code, "TENANT_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private DossierFixture createDossier(String code, String userId, String label) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/dossiers")
                        .with(user(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dossierPayload(code, label)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new DossierFixture(
                extractString(body, "id"),
                extractString(body, "reference"),
                extractLong(body, "version"));
    }

    private static RequestPostProcessor user(String userId, String... roles) {
        return oidcLogin()
                .idToken(token -> token
                        .subject(userId)
                        .claim("tenant_id", TENANT_ID)
                        .claim("email", userId + "@example.com")
                        .claim("name", userId))
                .authorities(Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    private static String typePayload(String code, boolean active) {
        return typePayload(code, active, "[]");
    }

    private static String typePayload(String code, boolean active, String fieldDefinitions) {
        return """
                {
                  "code": "%s",
                  "label": "Label %s",
                  "workflowKey": "workflow-%s",
                  "active": %s,
                  "allowUnknownFields": false,
                  "fieldDefinitions": %s
                }
                """.formatted(code, code, code.toLowerCase(), active, fieldDefinitions);
    }

    private static String dossierPayload(String code, String label) {
        return dossierPayload(code, label, "{}");
    }

    private static String dossierPayload(String code, String label, String fields) {
        return """
                {
                  "dossierTypeCode": "%s",
                  "label": "%s",
                  "fields": %s
                }
                """.formatted(code, label, fields);
    }

    private static String uniqueCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String extractString(String json, String field) {
        var matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON field: " + field + " in " + json);
        }
        return matcher.group(1);
    }

    private static long extractLong(String json, String field) {
        var matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON field: " + field + " in " + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    private record DossierFixture(String id, String reference, long version) {}
}
