package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentResult;
import be.myrmacta.domain.workflow.port.in.DeployDecisionTableUseCase;
import be.myrmacta.domain.workflow.port.in.GetDecisionTableDeploymentInfoUseCase;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
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
@RequestMapping("/api/dmn-decision-deployments")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
class DmnDecisionDeploymentController {

    private final DeployDecisionTableUseCase deployUseCase;
    private final GetDecisionTableDeploymentInfoUseCase infoUseCase;
    private final DmnDeploymentPort deploymentPort;
    private final TenantProperties tenantProperties;
    private final BpmnUploadProperties uploadProperties;

    DmnDecisionDeploymentController(
            DeployDecisionTableUseCase deployUseCase,
            GetDecisionTableDeploymentInfoUseCase infoUseCase,
            DmnDeploymentPort deploymentPort,
            TenantProperties tenantProperties,
            BpmnUploadProperties uploadProperties) {
        this.deployUseCase = deployUseCase;
        this.infoUseCase = infoUseCase;
        this.deploymentPort = deploymentPort;
        this.tenantProperties = tenantProperties;
        this.uploadProperties = uploadProperties;
    }

    @GetMapping("/{decisionKey}/xml")
    ResponseEntity<byte[]> deployedXml(@PathVariable String decisionKey) {
        return deploymentPort
                .findDeployedXml(tenantProperties.id(), decisionKey)
                .map(xml -> ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(xml))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/info")
    ResponseEntity<DecisionTableDeploymentInfoResponse> info(@RequestParam String decisionKey) {
        return infoUseCase
                .getDeploymentInfo(decisionKey)
                .map(DecisionTableDeploymentInfoResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<DecisionTableDeploymentResultResponse> deploy(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        if (!uploadProperties.isAllowed(file.getContentType())) {
            throw new IllegalArgumentException("content type not allowed: " + file.getContentType());
        }
        DecisionTableDeploymentResult result = deployUseCase.deploy(file.getOriginalFilename(), readBytes(file));
        return ResponseEntity
                .created(URI.create("/api/dmn-decision-deployments/info?decisionKey=" + result.decisionKey()))
                .body(DecisionTableDeploymentResultResponse.from(result));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read uploaded file: " + ex.getMessage());
        }
    }
}
