package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * S-STUB-8 (T-GUARD-4, R-SIM-EN-PROD) : le second drapeau du stub ne figure dans aucun gabarit de
 * configuration livré — il doit être posé volontairement par un opérateur, jamais copié par défaut.
 */
class SignatureStubFlagNotInTemplatesTest {

    private static final String FLAG_KEBAB = "allow-outside-local";
    private static final String FLAG_ENV = "ALLOW_OUTSIDE_LOCAL";

    @Test
    void secondFlagIsAbsentFromConfigurationTemplates() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        try (Stream<Path> files = Stream.concat(
                Files.walk(root.resolve("src/main/resources"), 1),
                Stream.of(root.resolve(".env.example"), root.resolve(".env.local.example"),
                        root.resolve("docker-compose.yml")))) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().matches("application.*\\.(yml|yaml|properties)|\\.env.*|docker-compose.*"))
                    .toList()) {
                String content = Files.readString(file);
                assertThat(content).as(file.toString()).doesNotContain(FLAG_KEBAB).doesNotContain(FLAG_ENV);
            }
        }
        assertThat(Files.exists(root.resolve("src/main/resources/application.yml"))).isTrue();
    }
}
