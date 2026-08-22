package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.port.in.DeployBpmnProcessUseCase;
import com.flowforge.domain.workflow.port.in.GetProcessDeploymentInfoUseCase;
import com.flowforge.infrastructure.config.BpmnUploadProperties;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final BpmnUploadProperties uploadProperties;

    WorkflowBpmnDeploymentController(
            DeployBpmnProcessUseCase deployUseCase,
            GetProcessDeploymentInfoUseCase infoUseCase,
            BpmnUploadProperties uploadProperties) {
        this.deployUseCase = deployUseCase;
        this.infoUseCase = infoUseCase;
        this.uploadProperties = uploadProperties;
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
