package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignerKind;
import be.myrmacta.domain.signature.port.in.DispatchSignatureOutcomesUseCase;
import be.myrmacta.domain.signature.port.in.HandleSignatureCallbackUseCase;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.workflow.port.in.CompleteTaskUseCase;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * TODO §148 (D-VIDEO-SIGN, issue #59) — la scène de la capsule C13 : l'exemple convention fournisseur installé par les
 * vrais cas d'usage, portail activé, et un dossier d'exemple qui attend sa relecture. Avec le fournisseur {@code stub},
 * le dossier va jusqu'à l'attente de la signature, puis jusqu'à « signée » par le retour normalisé du fournisseur (ce que
 * fait le webhook). Avec {@code yousign}, la scène crée le connecteur {@code signature} du bac à sable depuis ses clés, et
 * refuse de démarrer sans elles.
 */
class SignatureDemoPackIT {

    private static final String TENANT = "tenant_dev";
    private static final String SIGNER = "claire.lambert@imprimerie-lambert.example";
    private static final AccessContext.User ADMIN = AccessContext.user("demo-test", Set.of("TENANT_ADMIN", "WORKFLOW_ADMIN"));

    private static ConfigurableApplicationContext run(Path baseDir, String locale, String provider, String... extra) {
        SpringApplication application = new SpringApplication(MyrmactaApplication.class, OidcClientTestConfiguration.class);
        return application.run(Stream.concat(Stream.of(
                "--spring.profiles.active=test,embedded-db," + SignatureDemoPack.PROFILE,
                "--test.embedded-postgres.enabled=false",
                "--app.embedded-db.data-dir=" + baseDir,
                "--app.embedded-db.port=0",
                "--app.portal.enabled=true",
                "--app.portal.public-base-url=http://localhost:8080",
                "--app.signature.provider=" + provider,
                "--app.demo.locale=" + locale,
                "--server.port=0"), Stream.of(extra)).toArray(String[]::new));
    }

    @Test
    void withTheStub_theSampleCaseGoesFromReviewToSignatureThenToSigned(@TempDir Path baseDir) throws IOException {
        try (ConfigurableApplicationContext context = run(baseDir, "fr", "stub")) {
            assertThat(context.getBean(SignatureDemoPack.class).installed()).isTrue();
            assertThat(deployedProcess(context)).contains("name=\"Relire la convention\"").contains("name=\"Fournisseur\"");
            assertThat(signatureConnectors(context)).as("le stub n'a pas besoin du connecteur signature").isEmpty();

            Dossier dossier = sampleCase(context);
            assertThat(dossier.label()).isEqualTo("Convention — Imprimerie Lambert");
            String instance = context.getBean(DossierWorkflowLinkRepository.class).findByDossierIdOrderBySequence(dossier.id())
                    .getFirst().processInstanceId();
            assertThat(status(context, instance)).isEqualTo("EN_PREPARATION");

            TaskService tasks = context.getBean(TaskService.class);
            Task review = tasks.createTaskQuery().processInstanceId(instance).singleResult();
            assertThat(review.getTaskDefinitionKey()).isEqualTo("relecture");
            context.getBean(CompleteTaskUseCase.class).complete(review.getId(), dossier.id().toString(), Map.of(), ADMIN);

            SignatureRequestRepository signatures = context.getBean(SignatureRequestRepository.class);
            SignatureRequest request = signatures.findByDossierId(dossier.id()).getFirst();
            assertThat(signatures.findSigners(request.id())).singleElement().satisfies(signer -> {
                assertThat(signer.kind()).isEqualTo(SignerKind.EXTERNAL);
                assertThat(signer.email()).isEqualTo(SIGNER);
                assertThat(signer.displayName()).isEqualTo("Fournisseur");
            });
            assertThat(status(context, instance)).isEqualTo("EN_SIGNATURE");
            assertThat(context.getBean(DossierElementRepository.class).findByDossierIdOrderByAddedAtAsc(dossier.id()))
                    .as("la convention générée, jointe au dossier")
                    .anyMatch(e -> e.type() == ElementType.PRODUCED_DOC && e.storageRef().equals(request.sourceStorageRef()));

            // Le retour du fournisseur, comme le webhook le livre, puis la reprise du processus.
            assertThat(context.getBean(HandleSignatureCallbackUseCase.class).apply(new SignaturePort.SignatureCallbackEvent(
                    request.providerReference(), SignatureOutcome.SIGNED, Map.of()))).isTrue();
            context.getBean(DispatchSignatureOutcomesUseCase.class).dispatch(request.id());

            assertThat(context.getBean(RuntimeService.class).createProcessInstanceQuery().processInstanceId(instance).count())
                    .as("processus terminé").isZero();
            assertThat(status(context, instance)).isEqualTo("SIGNEE");
        }
    }

    @Test
    void inEnglishTheExampleIsTranslated_signerLabelIncluded(@TempDir Path baseDir) throws IOException {
        try (ConfigurableApplicationContext context = run(baseDir, "en", "stub")) {
            DossierType type = context.getBean(DossierTypeRepository.class).findByCode(TENANT, SignatureDemoPack.TYPE)
                    .orElseThrow();
            assertThat(type.label()).isEqualTo("Supplier agreement");
            assertThat(deployedProcess(context)).contains("name=\"Review the agreement\"").contains("name=\"Supplier\"")
                    .doesNotContain("Relire la convention");
            assertThat(sampleCase(context).label()).isEqualTo("Agreement — Lambert Printing");
        }
    }

    @Test
    void withYousign_theSceneCreatesTheSandboxSignatureConnectorFromItsKeys(@TempDir Path baseDir) {
        String secret = "whsec-" + "x".repeat(40);
        try (ConfigurableApplicationContext context = run(baseDir, "fr", "yousign",
                "--app.demo.signature.yousign-api-key=sandbox-key-for-test",
                "--app.demo.signature.webhook-secret=" + secret)) {
            assertThat(signatureConnectors(context)).singleElement().satisfies(instance -> {
                assertThat(instance.enabled()).isTrue();
                assertThat(instance.config()).containsEntry("webhookAuthType", "YOUSIGN_HMAC")
                        .containsEntry("yousignEnvironment", "sandbox")
                        .containsEntry("yousignApiKey", "sandbox-key-for-test")
                        .containsEntry("webhookSecret", secret);
            });
        }
    }

    @Test
    void withYousign_theSceneRefusesToStartWithoutItsKeys_andSaysWhatToSet(@TempDir Path baseDir) {
        assertThatThrownBy(() -> run(baseDir, "fr", "yousign").close())
                .hasStackTraceContaining("YOUSIGN_SANDBOX_API_KEY")
                .hasStackTraceContaining("YOUSIGN_WEBHOOK_SECRET");
    }

    private static Dossier sampleCase(ConfigurableApplicationContext context) {
        return context.getBean(DossierRepository.class).findByDossierTypeCode(TENANT, SignatureDemoPack.TYPE).getFirst();
    }

    private static String status(ConfigurableApplicationContext context, String instance) {
        return context.getBean(DossierWorkflowLinkRepository.class).findByProcessInstanceId(TENANT, instance).orElseThrow()
                .status();
    }

    private static List<ConnectorInstanceData> signatureConnectors(ConfigurableApplicationContext context) {
        return context.getBean(ConnectorInstanceRepository.class).listByTenantId(TENANT).stream()
                .filter(instance -> "signature".equals(instance.connectorType())).toList();
    }

    private static String deployedProcess(ConfigurableApplicationContext context) throws IOException {
        RepositoryService repository = context.getBean(RepositoryService.class);
        ProcessDefinition definition = repository.createProcessDefinitionQuery().processDefinitionKey(SignatureDemoPack.PROCESS)
                .processDefinitionTenantId(TENANT).latestVersion().singleResult();
        assertThat(definition).as("processus conventionFournisseur déployé").isNotNull();
        try (InputStream xml = repository.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName())) {
            return new String(xml.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
