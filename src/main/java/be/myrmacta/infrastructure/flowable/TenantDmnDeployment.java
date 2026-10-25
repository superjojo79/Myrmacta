package be.myrmacta.infrastructure.flowable;

import be.myrmacta.infrastructure.config.TenantProperties;
import java.io.IOException;
import org.flowable.dmn.api.DmnRepositoryService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * TODO §61 (introduction-dmn), M11 -- mirrors {@link TenantProcessDeployment} for shipped decision
 * tables: every {@code .dmn} file under {@code classpath*:/decisions/**} (currently
 * {@code grille-eligibilite-client.dmn}, the illustrative scenario from
 * https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-11-feature-introduction-dmn.md) is deployed directly on the real
 * {@link DmnRepositoryService} at startup, for every tenant, the same way shipped BPMN processes
 * bypass {@code FlowableBpmnParserAdapter} at boot -- these are FlowForge-authored fixtures, not
 * user-submitted content, so the JUEL allowlist is not the boundary here (it is still exercised by
 * a dedicated non-regression test on every shipped file, mirroring
 * FlowableBpmnParserAdapterShippedProcessesTest).
 */
@Component
class TenantDmnDeployment implements ApplicationRunner {

    private final DmnRepositoryService dmnRepositoryService;
    private final ResourcePatternResolver resourcePatternResolver;
    private final TenantProperties tenantProperties;

    TenantDmnDeployment(
            DmnRepositoryService dmnRepositoryService,
            ResourcePatternResolver resourcePatternResolver,
            TenantProperties tenantProperties) {
        this.dmnRepositoryService = dmnRepositoryService;
        this.resourcePatternResolver = resourcePatternResolver;
        this.tenantProperties = tenantProperties;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] resources = resourcePatternResolver.getResources("classpath*:/decisions/**/*.dmn");
        if (resources.length == 0) {
            return;
        }
        var deployment = dmnRepositoryService.createDeployment()
                .name("tenant-decisions-" + tenantProperties.id())
                .tenantId(tenantProperties.id());
        for (Resource resource : resources) {
            deployment.addInputStream(resource.getFilename(), resource.getInputStream());
        }
        deployment.deploy();
    }
}
