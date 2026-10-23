package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@code frontend/e2e/pilotage.spec.ts} keeps its own {@code A4_CSP} literal in sync with
 * {@link SecurityHeadersCustomizer#CONTENT_SECURITY_POLICY} by convention only (a code comment,
 * "Must stay equal to ..."), because Playwright's E2E environment never exercises the real
 * Spring Security header writer (see {@code A-E2E-CSP-INJECT}, https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/FILE-ATTENTE.md). Nothing
 * previously caught the two drifting apart. This test reads the TS literal back and compares it
 * to the Java constant, so a future edit to either side without the other fails CI (TODO §64,
 * revue adversariale post-merge PR #128).
 */
class FrontendCspLiteralConsistencyTest {

    private static final Path SPEC_FILE = Path.of("frontend/e2e/pilotage.spec.ts");
    private static final Pattern A4_CSP_LITERAL = Pattern.compile("const A4_CSP =\\s*\"([^\"]+)\"");

    @Test
    void a4CspLiteralInPlaywrightSpec_matchesJavaConstant() throws IOException {
        String content = Files.readString(SPEC_FILE);
        Matcher matcher = A4_CSP_LITERAL.matcher(content);

        assertThat(matcher.find())
                .as("const A4_CSP = \"...\" not found in %s -- update this test's regex if the"
                        + " TS literal's declaration shape changed", SPEC_FILE)
                .isTrue();
        assertThat(matcher.group(1))
                .as("%s A4_CSP has drifted from SecurityHeadersCustomizer.CONTENT_SECURITY_POLICY"
                        + " -- keep both in sync (TODO §64)", SPEC_FILE)
                .isEqualTo(SecurityHeadersCustomizer.CONTENT_SECURITY_POLICY);
    }
}
