package be.myrmacta.domain.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Revue adversariale #201 (I1) — le dialogue « Démarrer un workflow » refuse les noms réservés avant l'envoi : sa liste
 * ({@code RESERVED_START_VARIABLES}) doit rester celle du serveur, qui reste l'autorité.
 */
class ReservedVariablesFrontendConsistencyTest {

    private static final Path START_VARIABLES = Path.of("frontend/src/components/workflow/startVariables.ts");
    private static final Pattern LIST = Pattern.compile("RESERVED_START_VARIABLES = \\[([^\\]]*)\\]");
    private static final Pattern NAME = Pattern.compile("'([A-Za-z_][A-Za-z0-9_]*)'");

    @Test
    void theSpaListsExactlyTheServerReservedNames() throws IOException {
        Matcher list = LIST.matcher(Files.readString(START_VARIABLES));
        assertThat(list.find()).as("RESERVED_START_VARIABLES dans " + START_VARIABLES).isTrue();
        List<String> names = new ArrayList<>();
        Matcher name = NAME.matcher(list.group(1));
        while (name.find()) {
            names.add(name.group(1));
        }
        assertThat(names).containsExactlyInAnyOrderElementsOf(WorkflowVariables.reservedKeys());
    }
}
