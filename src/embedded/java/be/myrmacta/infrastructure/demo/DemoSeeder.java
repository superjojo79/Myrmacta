package be.myrmacta.infrastructure.demo;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * TODO §127 (D-DEMO-PACKS, issue #55) — orchestrateur de la démo. Profil {@code demo} : tous les paquets ; profil
 * {@code demo-mailroom} : le seul paquet mail room (§113). Au démarrage, installe dans l'ordre chaque paquet absent ; un
 * paquet déjà installé n'est pas touché ({@code scripts/demo.sh reset} vide la base pour rejouer la démo). Un paquet en
 * échec arrête la démo : les suivants peuvent dépendre de lui.
 */
@Component
@Profile({"demo", "demo-mailroom", VideoMailRoomDemoPack.PROFILE})
public class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final List<DemoPack> packs;

    public DemoSeeder(List<DemoPack> packs) {
        Set<Integer> orders = new HashSet<>();
        for (DemoPack pack : packs) {
            if (!orders.add(pack.order())) {
                throw new IllegalStateException("demo: two packs share the order " + pack.order());
            }
        }
        this.packs = packs.stream().sorted(Comparator.comparingInt(DemoPack::order)).toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        for (DemoPack pack : packs) {
            if (pack.installed()) {
                log.info("demo: pack '{}' already installed, nothing done", pack.code());
                continue;
            }
            try {
                pack.install();
            } catch (RuntimeException ex) {
                throw new IllegalStateException("demo: pack '" + pack.code() + "' failed to install", ex);
            }
            log.info("demo: pack '{}' installed", pack.code());
        }
    }
}
