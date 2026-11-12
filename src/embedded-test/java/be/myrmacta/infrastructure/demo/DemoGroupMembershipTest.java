package be.myrmacta.infrastructure.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.port.in.RecordUserLoginUseCase;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/** TODO §127 (D-DEMO-MEMBERS) — le compte de démo rejoint les groupes de la démo à sa connexion. */
class DemoGroupMembershipTest {

    private final UserGroupsUseCase groups = mock(UserGroupsUseCase.class);
    private final RecordUserLoginUseCase directory = mock(RecordUserLoginUseCase.class);

    private DemoGroupMembership membership(String members) {
        return new DemoGroupMembership(groups, directory, new TenantProperties("tenant_dev"), members);
    }

    private static AuthenticationSuccessEvent login(String username, String subject, String tenant) {
        OidcIdToken token = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", subject, "preferred_username", username, "tenant_id", tenant));
        return new AuthenticationSuccessEvent(new TestingAuthenticationToken(
                new DefaultOidcUser(List.of(), token), null, "ROLE_TENANT_ADMIN"));
    }

    private static UserGroup group(String code, String... members) {
        return new UserGroup("tenant_dev", code, code, null, Set.of(members));
    }

    @Test
    void theDemoAccountIsFirstKnownToTheDirectory_thenJoinsEveryGroupItIsNotYetIn() {
        when(groups.list()).thenReturn(List.of(group("COURRIER"), group("COMPTABILITE", "sub-demo")));

        membership("demo").onLogin(login("demo", "sub-demo", "tenant_dev"));

        InOrder order = inOrder(directory, groups);
        order.verify(directory).record(eq("sub-demo"), anyString(), any(), eq(Set.of("TENANT_ADMIN")));
        order.verify(groups).addMember("COURRIER", "sub-demo");
        verify(groups, never()).addMember("COMPTABILITE", "sub-demo");
    }

    @Test
    void anotherAccountJoinsNothing() {
        membership("demo").onLogin(login("manager", "sub-manager", "tenant_dev"));

        verifyNoInteractions(groups, directory);
    }

    @Test
    void aDemoAccountOfAnotherTenantJoinsNothing() {
        membership("demo").onLogin(login("demo", "sub-demo", "autre_tenant"));

        verifyNoInteractions(groups, directory);
    }

    @Test
    void aFailureNeverBlocksTheLogin() {
        when(groups.list()).thenThrow(new IllegalStateException("base indisponible"));

        membership("demo, manager").onLogin(login("manager", "sub-manager", "tenant_dev"));

        verify(groups, never()).addMember(anyString(), anyString());
    }
}
