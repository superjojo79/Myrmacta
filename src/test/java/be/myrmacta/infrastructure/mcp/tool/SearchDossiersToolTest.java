package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.DossierPage;
import be.myrmacta.domain.dossier.model.SearchDossiersQuery;
import be.myrmacta.domain.dossier.port.in.SearchDossiersUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SearchDossiersToolTest {

    private static final String TENANT = "tenant_dev";

    private SearchDossiersUseCase searchDossiersUseCase;
    private SearchDossiersTool tool;

    @BeforeEach
    void setUp() {
        searchDossiersUseCase = mock(SearchDossiersUseCase.class);
        tool = new SearchDossiersTool(searchDossiersUseCase, new TenantProperties(TENANT), new McpAccessContextFactory());
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void relaysToSearchDossiersQuery() {
        when(searchDossiersUseCase.search(eq(TENANT), any(), any())).thenReturn(DossierPage.empty(0, 20));

        tool.searchDossiers(null, "SINISTRE", "DOS-", null, null, null);

        var captor = org.mockito.ArgumentCaptor.forClass(SearchDossiersQuery.class);
        verify(searchDossiersUseCase).search(eq(TENANT), captor.capture(), any());
        assertThat(captor.getValue().dossierTypeCode()).isEqualTo("SINISTRE");
        assertThat(captor.getValue().referencePrefix()).isEqualTo("DOS-");
        assertThat(captor.getValue().page()).isZero();
        assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    void sizeOver100_isRejected() {
        assertThatThrownBy(() -> tool.searchDossiers(null, null, null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankStatus_isTreatedAsNoFilter() {
        when(searchDossiersUseCase.search(eq(TENANT), any(), any())).thenReturn(DossierPage.empty(0, 20));

        tool.searchDossiers("   ", null, null, null, null, null);

        var captor = org.mockito.ArgumentCaptor.forClass(SearchDossiersQuery.class);
        verify(searchDossiersUseCase).search(eq(TENANT), captor.capture(), any());
        assertThat(captor.getValue().status()).isNull();
    }

    @Test
    void validStatus_isParsedIntoQuery() {
        when(searchDossiersUseCase.search(eq(TENANT), any(), any())).thenReturn(DossierPage.empty(0, 20));

        tool.searchDossiers("OUVERT", null, null, null, null, null);

        var captor = org.mockito.ArgumentCaptor.forClass(SearchDossiersQuery.class);
        verify(searchDossiersUseCase).search(eq(TENANT), captor.capture(), any());
        assertThat(captor.getValue().status()).isEqualTo(be.myrmacta.domain.dossier.model.DossierStatus.OUVERT);
    }

    @Test
    void fieldFilters_areParsedIntoConditions_andABadOperatorIsRefused() {
        when(searchDossiersUseCase.search(eq(TENANT), any(), any())).thenReturn(DossierPage.empty(0, 20));

        tool.searchDossiers(null, "SINISTRE", null,
                java.util.Map.of("contrat", "C-42", "montant.gte", 5000), null, null);

        var captor = org.mockito.ArgumentCaptor.forClass(SearchDossiersQuery.class);
        verify(searchDossiersUseCase).search(eq(TENANT), captor.capture(), any());
        assertThat(captor.getValue().fieldConditions()).extracting(c -> c.field() + ":" + c.op() + ":" + c.values())
                .containsExactly("contrat:EQ:[C-42]", "montant:GTE:[5000]");

        assertThatThrownBy(() -> tool.searchDossiers(null, "SINISTRE", null, java.util.Map.of("montant.between", "1"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
