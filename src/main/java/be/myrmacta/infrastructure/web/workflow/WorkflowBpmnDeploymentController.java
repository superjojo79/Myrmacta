package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.workflow.model.BpmnDeploymentResult;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.in.GetProcessDeploymentInfoUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.infrastructure.config.BpmnUploadProperties;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/workflow-bpmn-deployments")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
class WorkflowBpmnDeploymentController {

    private final DeployBpmnProcessUseCase deployUseCase;
    private final GetProcessDeploymentInfoUseCase infoUseCase;
    private final WorkflowDeploymentPort deploymentPort;
    private final TenantProperties tenantProperties;
    private final BpmnUploadProperties uploadProperties;

    WorkflowBpmnDeploymentController(
            DeployBpmnProcessUseCase deployUseCase,
            GetProcessDeploymentInfoUseCase infoUseCase,
            WorkflowDeploymentPort deploymentPort,
            TenantProperties tenantProperties,
            BpmnUploadProperties uploadProperties) {
        this.deployUseCase = deployUseCase;
        this.infoUseCase = infoUseCase;
        this.deploymentPort = deploymentPort;
        this.tenantProperties = tenantProperties;
        this.uploadProperties = uploadProperties;
    }

    @GetMapping("/{processDefinitionKey}/xml")
    ResponseEntity<byte[]> deployedXml(@PathVariable String processDefinitionKey) {
        return deploymentPort
                .findDeployedXml(tenantProperties.id(), processDefinitionKey)
                .map(xml -> ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(xml))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/info")
    ResponseEntity<ProcessDeploymentInfoResponse> info(@RequestParam String processDefinitionKey) {
        return infoUseCase
                .getDeploymentInfo(processDefinitionKey)
                .map(ProcessDeploymentInfoResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<BpmnDeploymentResultResponse> deploy(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        if (!uploadProperties.isAllowed(file.getContentType())) {
            throw new IllegalArgumentException("content type not allowed: " + file.getContentType());
        }
        BpmnDeploymentResult result = deployUseCase.deploy(file.getOriginalFilename(), readBytes(file));
        return ResponseEntity
                .created(URI.create(
                        "/api/workflow-bpmn-deployments/info?processDefinitionKey=" + result.processDefinitionKey()))
                .body(BpmnDeploymentResultResponse.from(result));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read uploaded file: " + ex.getMessage());
        }
    }
}
