package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.in.ListDossierTypesUseCase;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
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

class ListDossierTypesToolTest {

    private static final String TENANT = "tenant_dev";

    private ListDossierTypesUseCase listDossierTypesUseCase;
    private ListDossierTypesTool tool;

    @BeforeEach
    void setUp() {
        listDossierTypesUseCase = mock(ListDossierTypesUseCase.class);
        tool = new ListDossierTypesTool(listDossierTypesUseCase, new TenantProperties(TENANT), new McpAccessContextFactory());
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
    void pageAndSizeAbsent_defaultsToPageQueryDefaults() {
        PageResult<DossierType> page = PageResult.of(List.of(), 0, PageQuery.DEFAULT_SIZE, 0);
        when(listDossierTypesUseCase.list(eq(TENANT), any(), eq(PageQuery.defaults()))).thenReturn(page);

        PageResult<DossierType> result = tool.listDossierTypes(null, null);

        assertThat(result).isSameAs(page);
    }

    @Test
    void pageAndSizeProvided_relayedAsGiven() {
        PageResult<DossierType> page = PageResult.of(List.of(), 2, 5, 0);
        when(listDossierTypesUseCase.list(eq(TENANT), any(), eq(PageQuery.of(2, 5)))).thenReturn(page);

        tool.listDossierTypes(2, 5);

        verify(listDossierTypesUseCase).list(eq(TENANT), any(), eq(PageQuery.of(2, 5)));
    }

    @Test
    void onlyPageProvided_sizeDefaultsTo20() {
        when(listDossierTypesUseCase.list(eq(TENANT), any(), eq(PageQuery.of(3, 20))))
                .thenReturn(PageResult.of(List.of(), 3, 20, 0));

        tool.listDossierTypes(3, null);

        verify(listDossierTypesUseCase).list(eq(TENANT), any(), eq(PageQuery.of(3, 20)));
    }

    @Test
    void onlySizeProvided_pageDefaultsTo0() {
        when(listDossierTypesUseCase.list(eq(TENANT), any(), eq(PageQuery.of(0, 50))))
                .thenReturn(PageResult.of(List.of(), 0, 50, 0));

        tool.listDossierTypes(null, 50);

        verify(listDossierTypesUseCase).list(eq(TENANT), any(), eq(PageQuery.of(0, 50)));
    }
}
