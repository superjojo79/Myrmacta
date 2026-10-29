package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.workflow.model.StartVariableDeclaration;
import be.myrmacta.domain.workflow.port.out.StartVariableCatalogPort;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** TODO §111 (issue #187, D-START-DECLARED) — formulaire du dialogue « Démarrer un workflow ». */
@RestController
@RequestMapping("/api/workflow-start-variables")
class WorkflowStartVariableController {

    private final StartVariableCatalogPort catalog;

    WorkflowStartVariableController(StartVariableCatalogPort catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    StartVariablesResponse list(@RequestParam String processDefinitionKey) {
        if (processDefinitionKey.isBlank()) {
            throw new IllegalArgumentException("processDefinitionKey is required");
        }
        return StartVariablesResponse.from(catalog.findDeclared(processDefinitionKey));
    }

    record StartVariablesResponse(List<DeclaredVariable> variables) {
        static StartVariablesResponse from(List<StartVariableDeclaration> declarations) {
            return new StartVariablesResponse(declarations.stream()
                    .map(declaration -> new DeclaredVariable(
                            declaration.name(), declaration.label(), declaration.required()))
                    .toList());
        }
    }

    record DeclaredVariable(String name, String label, boolean required) {}
}
