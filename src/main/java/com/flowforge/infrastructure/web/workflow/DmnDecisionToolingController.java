package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.workflow.model.DecisionEvaluationResult;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.model.DecisionTableVersionInfo;
import com.flowforge.domain.workflow.model.DecisionTestCase;
import com.flowforge.domain.workflow.model.DecisionTestReport;
import com.flowforge.domain.workflow.port.in.EvaluateDecisionUseCase;
import com.flowforge.domain.workflow.port.in.ListDecisionTableVersionsUseCase;
import com.flowforge.domain.workflow.port.in.RedeployDecisionTableUseCase;
import com.flowforge.domain.workflow.port.in.TestDecisionUseCase;
import com.flowforge.infrastructure.flowable.DmnCsvTestCaseParser;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dmn-decision-deployments")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
class DmnDecisionToolingController {

    private static final Logger log = LoggerFactory.getLogger(DmnDecisionToolingController.class);

    private final EvaluateDecisionUseCase evaluateUseCase;
    private final TestDecisionUseCase testUseCase;
    private final ListDecisionTableVersionsUseCase listVersionsUseCase;
    private final RedeployDecisionTableUseCase redeployUseCase;
    private final DmnCsvTestCaseParser csvParser;
    private final AccessContextFactory accessContextFactory;

    DmnDecisionToolingController(
            EvaluateDecisionUseCase evaluateUseCase,
            TestDecisionUseCase testUseCase,
            ListDecisionTableVersionsUseCase listVersionsUseCase,
            RedeployDecisionTableUseCase redeployUseCase,
            DmnCsvTestCaseParser csvParser,
            AccessContextFactory accessContextFactory) {
        this.evaluateUseCase = evaluateUseCase;
        this.testUseCase = testUseCase;
        this.listVersionsUseCase = listVersionsUseCase;
        this.redeployUseCase = redeployUseCase;
        this.csvParser = csvParser;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping(path = "/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE)
    DecisionEvaluationResult evaluate(
            @RequestParam String decisionKey,
            @RequestParam(required = false) Integer version,
            @RequestBody EvaluateDecisionRequest body,
            Authentication authentication) {
        return evaluateUseCase.evaluate(
                accessContextFactory.user(authentication),
                decisionKey,
                version,
                body == null || body.inputs() == null ? Map.of() : body.inputs());
    }

    @PostMapping(path = "/test", consumes = MediaType.APPLICATION_JSON_VALUE)
    DecisionTestReport testJson(
            @RequestParam String decisionKey,
            @RequestParam(required = false) Integer version,
            @RequestBody TestDecisionRequest body,
            Authentication authentication) {
        List<DecisionTestCase> cases = body == null || body.cases() == null ? List.of() : body.cases();
        return testUseCase.test(accessContextFactory.user(authentication), decisionKey, version, cases, false);
    }

    @PostMapping(path = "/test", consumes = {"text/csv", "text/plain"})
    DecisionTestReport testCsv(
            @RequestParam String decisionKey,
            @RequestParam(required = false) Integer version,
            @RequestBody String csv,
            Authentication authentication) {
        return testUseCase.test(
                accessContextFactory.user(authentication),
                decisionKey,
                version,
                csvParser.parse(csv),
                true);
    }

    @GetMapping("/{decisionKey}/versions")
    List<DecisionTableVersionInfo> versions(@PathVariable String decisionKey, Authentication authentication) {
        return listVersionsUseCase.list(accessContextFactory.user(authentication), decisionKey);
    }

    @PostMapping("/{decisionKey}/redeploy/{version}")
    ResponseEntity<DecisionTableDeploymentResult> redeploy(
            @PathVariable String decisionKey, @PathVariable int version, Authentication authentication) {
        log.info("dmn.redeploy decisionKey={} version={}", decisionKey, version);
        DecisionTableDeploymentResult result =
                redeployUseCase.redeploy(accessContextFactory.user(authentication), decisionKey, version);
        return ResponseEntity.ok(result);
    }

    public record EvaluateDecisionRequest(Map<String, Object> inputs) {}

    public record TestDecisionRequest(List<DecisionTestCase> cases) {}
}
