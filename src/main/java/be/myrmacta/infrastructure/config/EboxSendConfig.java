package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.ebox.port.out.EboxChannel;
import be.myrmacta.domain.ebox.port.out.EboxSendAuditTrail;
import be.myrmacta.domain.ebox.port.out.EboxSendReservations;
import be.myrmacta.domain.ebox.usecase.SendEboxMessageUseCaseImpl;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** TODO §155 — envoi eBox : une voie par route (entreprise, citoyen), journal d'audit des envois. */
@Configuration
class EboxSendConfig {

    @Bean
    SendEboxMessageUseCaseImpl sendEboxMessageUseCase(
            TenantProperties tenantProperties, List<EboxChannel> channels, EboxSendAuditTrail audit,
            EboxSendReservations reservations) {
        return new SendEboxMessageUseCaseImpl(tenantProperties.id(), channels, audit, reservations, Clock.systemUTC());
    }
}
