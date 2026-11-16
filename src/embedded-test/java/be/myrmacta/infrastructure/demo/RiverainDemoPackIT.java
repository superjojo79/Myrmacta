package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.port.in.ListDocumentTemplateUseCase;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §143 (issue #101) — la scène des capsules C5 et C6 et du screenshot S13 : l'exemple autorisation riverain installé
 * par les vrais cas d'usage, portail activé, en français puis en anglais (variante {@code en/}). Le processus déployé est
 * la variante « avec portail » ; la capsule C6 publie elle-même la variante avec pré-vérification.
 */
class RiverainDemoPackIT {

    private static ConfigurableApplicationContext run(Path baseDir, String locale, String... extra) {
        SpringApplication application = new SpringApplication(MyrmactaApplication.class, OidcClientTestConfiguration.class);
        return application.run(java.util.stream.Stream.concat(java.util.stream.Stream.of(
                "--spring.profiles.active=test,embedded-db," + RiverainDemoPack.PROFILE,
                "--test.embedded-postgres.enabled=false",
                "--app.embedded-db.data-dir=" + baseDir,
                "--app.embedded-db.port=0",
                "--app.portal.enabled=true",
                "--app.portal.public-base-url=http://localhost:8080",
                "--app.demo.locale=" + locale,
                "--server.port=0"), java.util.stream.Stream.of(extra)).toArray(String[]::new));
    }

    @Test
    void theSceneInstallsTheExampleWithThePortalVariant(@TempDir Path baseDir) throws IOException {
        try (ConfigurableApplicationContext context = run(baseDir, "fr")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(context.getBean(RiverainDemoPack.class).installed()).isTrue();

            DossierType type = context.getBean(DossierTypeRepository.class).findByCode("tenant_dev", "AUTORISATION_RIVERAIN")
                    .orElseThrow();
            assertThat(type.pieceTypes()).extracting(PieceType::key)
                    .containsExactly("preuveDomicile", "certificatImmatriculation", "pieceIdentite");
            assertThat(count(jdbc, "select count(*) from task_question_schema where schema_key like '%autorisation-riverain'"))
                    .isEqualTo(3);
            assertThat(count(jdbc, "select count(*) from document_template "
                    + "where template_key = 'decision-autorisation-riverain' and status = 'ACTIVE'")).isEqualTo(1);
            assertThat(count(jdbc, "select count(*) from notification_template "
                    + "where template_key = 'demande-informations-riverain' and status = 'ACTIVE'")).isEqualTo(1);
            assertThat(deployedProcess(context)).as("la variante avec portail")
                    .contains("flowable:portalRecipient=\"dossierField:email\"");
        }
    }

    @Test
    void inEnglishTheExampleIsTranslated(@TempDir Path baseDir) throws IOException {
        try (ConfigurableApplicationContext context = run(baseDir, "en")) {
            DossierType type = context.getBean(DossierTypeRepository.class).findByCode("tenant_dev", "AUTORISATION_RIVERAIN")
                    .orElseThrow();
            assertThat(type.label()).isEqualTo("Resident parking permit (vehicle)");
            assertThat(deployedProcess(context)).contains("name=\"Review the request\"").doesNotContain("Instruire la demande");
            // Le contenu est un objet volumineux en base : il se relit par le cas d'usage.
            assertThat(context.getBean(ListDocumentTemplateUseCase.class)
                    .list("tenant_dev", "decision-autorisation-riverain", true, PageQuery.of(0, 1)).content())
                    .singleElement().extracting(DocumentTemplate::htmlContent).asString()
                    .contains("Resident parking permit request — decision");
        }
    }

    @Test
    void forTheCapsuleC6TheSceneDeploysThePrecheckVariant_translatedInEnglish(@TempDir Path baseDir) throws IOException {
        // Revue de clôture de #101 : la capsule publiait elle-même le BPMN brut, donc en français dans la version anglaise.
        try (ConfigurableApplicationContext context = run(baseDir, "en", "--app.demo.riverain.variant=preverification",
                "--app.precheck.enabled=true", "--app.precheck.base-url=http://localhost:11434/v1",
                "--app.precheck.model=test-model")) {
            assertThat(deployedProcess(context)).as("la variante avec pré-vérification, traduite")
                    .contains("name=\"AI pre-check\"").contains("name=\"Review the request\"")
                    .doesNotContain("flowable:portalRecipient").doesNotContain("Instruire la demande");
        }
    }

    private static String deployedProcess(ConfigurableApplicationContext context) throws IOException {
        RepositoryService repository = context.getBean(RepositoryService.class);
        ProcessDefinition definition = repository.createProcessDefinitionQuery().processDefinitionKey("autorisationRiverain")
                .processDefinitionTenantId("tenant_dev").latestVersion().singleResult();
        assertThat(definition).as("processus autorisationRiverain déployé").isNotNull();
        try (InputStream xml = repository.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName())) {
            return new String(xml.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
