package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * TODO §95 (P1) : chaque {@link EnvironmentPostProcessor} de production est réellement chargé par Spring Boot.
 * Un post-processeur déclaré dans un fichier que Spring Boot ne lit pas ne s'exécute jamais, en silence :
 * ses garde-fous (secrets, en-têtes, mode dev…) n'existent alors que dans les tests unitaires.
 */
class EnvironmentPostProcessorRegistrationTest {

    @Test
    void everyProductionEnvironmentPostProcessor_isRegisteredInSpringFactories() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(EnvironmentPostProcessor.class));
        Set<String> declared = scanner.findCandidateComponents("be.myrmacta").stream()
                .map(definition -> definition.getBeanClassName())
                .filter(name -> !name.contains("$"))
                .filter(name -> !name.endsWith("Test") && !name.contains(".support."))
                .filter(EnvironmentPostProcessorRegistrationTest::isProductionClass)
                .collect(Collectors.toSet());

        List<String> registered = SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class,
                getClass().getClassLoader());

        assertThat(declared).isNotEmpty();
        assertThat(registered).containsAll(declared);
    }

    private static boolean isProductionClass(String className) {
        String resource = className.replace('.', '/') + ".class";
        var url = EnvironmentPostProcessorRegistrationTest.class.getClassLoader().getResource(resource);
        return url != null && !url.getPath().contains("/test-classes/");
    }
}
