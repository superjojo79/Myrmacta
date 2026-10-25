package be.myrmacta.infrastructure.persistence.assistant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "assistant_usage")
class AssistantUsageJpaEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "tokens", nullable = false)
    private long tokens;

    protected AssistantUsageJpaEntity() {}

    AssistantUsageJpaEntity(String tenantId, LocalDate day, long tokens) {
        this.key = new Key(tenantId, day);
        this.tokens = tokens;
    }

    long tokens() {
        return tokens;
    }

    @Embeddable
    static class Key implements Serializable {
        @Column(name = "tenant_id", nullable = false, length = 64)
        private String tenantId;

        @Column(name = "usage_day", nullable = false)
        private LocalDate day;

        protected Key() {}

        Key(String tenantId, LocalDate day) {
            this.tenantId = tenantId;
            this.day = day;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(tenantId, k.tenantId) && Objects.equals(day, k.day);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, day);
        }
    }
}
