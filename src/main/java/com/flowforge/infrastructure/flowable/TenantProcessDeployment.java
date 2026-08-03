package com.flowforge.infrastructure.flowable;

import com.flowforge.infrastructure.config.TenantProperties;
import java.io.IOException;
import org.flowable.engine.RepositoryService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
class TenantProcessDeployment implements ApplicationRunner {

    private final RepositoryService repositoryService;
    private final ResourcePatternResolver resourcePatternResolver;
    private final TenantProperties tenantProperties;

    TenantProcessDeployment(
            RepositoryService repositoryService,
            ResourcePatternResolver resourcePatternResolver,
            TenantProperties tenantProperties) {
        this.repositoryService = repositoryService;
        this.resourcePatternResolver = resourcePatternResolver;
        this.tenantProperties = tenantProperties;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] resources = resourcePatternResolver.getResources("classpath*:/processes/**/*.bpmn20.xml");
        if (resources.length == 0) {
            return;
        }
        var deployment = repositoryService.createDeployment()
                .name("tenant-processes-" + tenantProperties.id())
                .tenantId(tenantProperties.id());
        for (Resource resource : resources) {
            deployment.addInputStream(resource.getFilename(), resource.getInputStream());
        }
        deployment.deploy();
    }
}
