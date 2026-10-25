package be.myrmacta.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** D-BRAND-NAME : titre et description visibles du contrat OpenAPI. */
@Configuration
class OpenApiInfoConfiguration {

    @Bean
    OpenAPI myrmactaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Myrmacta")
                        .description("L'intelligence collective au service de vos dossiers et décisions.")
                        .version("v0"));
    }
}
