package com.flowforge.domain.reporting.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SlaSummary(Instant from, Instant to, String groupBy, List<SlaSummaryBucket> buckets) {
    public SlaSummary {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(groupBy, "groupBy");
        buckets = buckets == null ? List.of() : List.copyOf(buckets);
    }
}
