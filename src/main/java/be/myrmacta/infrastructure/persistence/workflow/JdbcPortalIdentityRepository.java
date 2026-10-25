package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.portal.model.IdentityAssuranceLevel;
import be.myrmacta.domain.workflow.portal.model.IdentityMatch;
import be.myrmacta.domain.workflow.portal.model.IdentityVerification;
import be.myrmacta.domain.workflow.portal.model.PortalIdentityAttempt;
import be.myrmacta.domain.workflow.portal.model.PortalLinkIdentity;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** TODO §104 (D-BEAUTH-STORE) — tables {@code portal_identity_attempt} et {@code portal_link_identity} (changeSet 038). */
@Component
class JdbcPortalIdentityRepository implements PortalIdentityRepository {

    private static final String IDENTITY_COLUMNS = """
            portal_link_id, tenant_id, proof_hash, provider, assurance_level, given_name, family_name, nrn_enc,
            match_result, verified_at, expires_at, submitted_at""";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    JdbcPortalIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public void insertAttempt(PortalIdentityAttempt attempt) {
        jdbc.update("""
                insert into portal_identity_attempt (id, tenant_id, state_hash, portal_link_id, provider, nonce,
                    pkce_verifier_enc, created_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, attempt.id(), attempt.tenantId(), attempt.stateHash(), attempt.linkId(), attempt.provider(),
                attempt.nonce(), attempt.pkceVerifierEnc(), ts(attempt.createdAt()), ts(attempt.expiresAt()));
    }

    @Override
    public Optional<PortalIdentityAttempt> consumeAttempt(String stateHash, Instant now) {
        List<PortalIdentityAttempt> consumed = jdbc.query("""
                update portal_identity_attempt set consumed_at = ?
                where state_hash = ? and consumed_at is null and expires_at > ?
                returning id, tenant_id, state_hash, portal_link_id, provider, nonce, pkce_verifier_enc, created_at,
                    expires_at
                """, (rs, i) -> new PortalIdentityAttempt(rs.getObject("id", UUID.class), rs.getString("tenant_id"),
                        rs.getObject("portal_link_id", UUID.class), rs.getString("state_hash"), rs.getString("provider"),
                        rs.getString("nonce"), rs.getString("pkce_verifier_enc"), instant(rs, "created_at"),
                        instant(rs, "expires_at")),
                ts(now), stateHash, ts(now));
        return consumed.stream().findFirst();
    }

    @Override
    public void saveIdentity(PortalLinkIdentity identity) {
        jdbc.update("delete from portal_link_identity where portal_link_id = ?", identity.linkId());
        jdbc.update("insert into portal_link_identity (" + IDENTITY_COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                identity.linkId(), identity.tenantId(), identity.proofHash(), identity.provider(), identity.level().name(),
                identity.givenName(), identity.familyName(), identity.nrnEnc(), identity.match().name(),
                ts(identity.verifiedAt()), ts(identity.expiresAt()), ts(identity.submittedAt()));
    }

    @Override
    public Optional<PortalLinkIdentity> findByProofHash(String proofHash) {
        return jdbc.query("select " + IDENTITY_COLUMNS + " from portal_link_identity where proof_hash = ?",
                JdbcPortalIdentityRepository::identity, proofHash).stream().findFirst();
    }

    @Override
    public Optional<PortalLinkIdentity> findByLinkId(UUID linkId) {
        return jdbc.query("select " + IDENTITY_COLUMNS + " from portal_link_identity where portal_link_id = ?",
                JdbcPortalIdentityRepository::identity, linkId).stream().findFirst();
    }

    @Override
    public boolean markSubmitted(UUID linkId, Instant now) {
        return jdbc.update("""
                update portal_link_identity set submitted_at = ?, proof_hash = null, nrn_enc = null, expires_at = null
                where portal_link_id = ? and submitted_at is null
                """, ts(now), linkId) == 1;
    }

    @Override
    public void deleteUnsubmitted(UUID linkId) {
        jdbc.update("delete from portal_link_identity where portal_link_id = ? and submitted_at is null", linkId);
    }

    @Override
    public int purgeExpired(Instant now) {
        int attempts = jdbc.update("delete from portal_identity_attempt where expires_at <= ? or consumed_at is not null",
                ts(now));
        int identities = jdbc.update("""
                delete from portal_link_identity i
                where i.submitted_at is null
                  and (i.expires_at <= ?
                       or not exists (select 1 from portal_link l where l.id = i.portal_link_id and l.status = 'ACTIVE'))
                """, ts(now));
        return attempts + identities;
    }

    @Override
    public Map<UUID, IdentityVerification> tracesByLinkId(Collection<UUID> linkIds) {
        Map<UUID, IdentityVerification> traces = new LinkedHashMap<>();
        if (linkIds == null || linkIds.isEmpty()) {
            return traces;
        }
        named.query("select " + IDENTITY_COLUMNS + " from portal_link_identity where portal_link_id in (:ids)",
                new MapSqlParameterSource("ids", linkIds), rs -> {
                    PortalLinkIdentity identity = identity(rs, 0);
                    traces.put(identity.linkId(), identity.trace());
                });
        return traces;
    }

    private static PortalLinkIdentity identity(ResultSet rs, int row) throws SQLException {
        return new PortalLinkIdentity(rs.getObject("portal_link_id", UUID.class), rs.getString("tenant_id"),
                rs.getString("proof_hash"), rs.getString("provider"),
                IdentityAssuranceLevel.valueOf(rs.getString("assurance_level")), rs.getString("given_name"),
                rs.getString("family_name"), rs.getString("nrn_enc"), IdentityMatch.valueOf(rs.getString("match_result")),
                instant(rs, "verified_at"), instant(rs, "expires_at"), instant(rs, "submitted_at"));
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
