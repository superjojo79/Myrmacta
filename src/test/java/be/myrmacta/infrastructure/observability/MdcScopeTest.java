package be.myrmacta.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcScopeTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void putsKeysAndRestoresParentOnClose() {
        MDC.put(MdcScope.TENANT_ID, "parent");
        try (MdcScope ignored = MdcScope.tenant("child").dossier("d1").processInstance("pi-1")) {
            assertThat(MDC.get(MdcScope.TENANT_ID)).isEqualTo("child");
            assertThat(MDC.get(MdcScope.DOSSIER_ID)).isEqualTo("d1");
            assertThat(MDC.get(MdcScope.PROCESS_INSTANCE_ID)).isEqualTo("pi-1");
        }
        assertThat(MDC.get(MdcScope.TENANT_ID)).isEqualTo("parent");
        assertThat(MDC.get(MdcScope.DOSSIER_ID)).isNull();
        assertThat(MDC.get(MdcScope.PROCESS_INSTANCE_ID)).isNull();
    }

    @Test
    void ignoresNullAndBlankValues() {
        try (MdcScope ignored = MdcScope.tenant("t").dossier((String) null).processInstance("  ")) {
            assertThat(MDC.get(MdcScope.TENANT_ID)).isEqualTo("t");
            assertThat(MDC.get(MdcScope.DOSSIER_ID)).isNull();
            assertThat(MDC.get(MdcScope.PROCESS_INSTANCE_ID)).isNull();
        }
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
