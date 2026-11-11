package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TODO §127 (D-DEMO-PACKS) — l'orchestrateur installe, dans l'ordre, chaque paquet pas encore installé. */
class DemoSeederTest {

    private final List<String> installed = new ArrayList<>();

    private DemoPack pack(String code, int order, boolean alreadyInstalled) {
        return new DemoPack() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public boolean installed() {
                return alreadyInstalled;
            }

            @Override
            public void install() {
                installed.add(code);
            }
        };
    }

    @Test
    void installsThePacksInTheirOrder_notInTheOrderSpringGivesThem() {
        new DemoSeeder(List.of(pack("etats", 40, false), pack("courrier", 10, false), pack("onboarding", 20, false)))
                .seed();

        assertThat(installed).containsExactly("courrier", "onboarding", "etats");
    }

    @Test
    void skipsAPackAlreadyInstalled_andStillInstallsTheOthers() {
        new DemoSeeder(List.of(pack("courrier", 10, true), pack("onboarding", 20, false))).seed();

        assertThat(installed).containsExactly("onboarding");
    }

    @Test
    void aFailingPackStopsTheDemo_withItsCode() {
        DemoPack broken = new DemoPack() {
            @Override
            public String code() {
                return "onboarding";
            }

            @Override
            public int order() {
                return 20;
            }

            @Override
            public boolean installed() {
                return false;
            }

            @Override
            public void install() {
                throw new IllegalStateException("boom");
            }
        };

        assertThatThrownBy(() -> new DemoSeeder(List.of(pack("courrier", 10, false), broken, pack("etats", 40, false)))
                .seed())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("onboarding");
        assertThat(installed).containsExactly("courrier");
    }

    @Test
    void twoPacksCannotShareAnOrder() {
        assertThatThrownBy(() -> new DemoSeeder(List.of(pack("a", 10, false), pack("b", 10, false))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10");
    }
}
