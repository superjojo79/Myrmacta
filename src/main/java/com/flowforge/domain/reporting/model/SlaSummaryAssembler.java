package com.flowforge.domain.reporting.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SlaSummaryAssembler {

    public static final String GROUP_BY_TYPE = "dossierTypeCode";
    public static final String GROUP_BY_STATUS = "status";

    private SlaSummaryAssembler() {}

    public static String resolveGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank() || GROUP_BY_TYPE.equals(groupBy)) {
            return GROUP_BY_TYPE;
        }
        if (GROUP_BY_STATUS.equals(groupBy)) {
            return GROUP_BY_STATUS;
        }
        throw new IllegalArgumentException("groupBy must be dossierTypeCode or status");
    }

    public static SlaSummary assemble(
            Instant from,
            Instant to,
            String groupBy,
            List<TypeSlaMeta> types,
            List<DossierSlaRow> rows,
            Instant now) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(now, "now");
        String resolved = resolveGroupBy(groupBy);
        Map<String, TypeSlaMeta> typeByCode = types == null
                ? Map.of()
                : types.stream().collect(Collectors.toMap(TypeSlaMeta::code, t -> t, (a, b) -> a));
        List<DossierSlaRow> current = rows == null ? List.of() : rows;

        Map<String, List<DossierSlaRow>> grouped = new LinkedHashMap<>();
        for (DossierSlaRow row : current) {
            String key = GROUP_BY_TYPE.equals(resolved) ? row.typeCode() : row.status().name();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        List<SlaSummaryBucket> buckets = grouped.entrySet().stream()
                .map(entry -> toBucket(entry.getKey(), entry.getValue(), typeByCode, resolved, now))
                .sorted(Comparator.comparingLong(SlaSummaryBucket::volume)
                        .reversed()
                        .thenComparing(SlaSummaryBucket::key))
                .toList();
        return new SlaSummary(from, to, resolved, buckets);
    }

    private static SlaSummaryBucket toBucket(
            String key,
            List<DossierSlaRow> rows,
            Map<String, TypeSlaMeta> typeByCode,
            String groupBy,
            Instant now) {
        long onTrack = 0;
        long atRisk = 0;
        long breached = 0;
        long unscored = 0;
        for (DossierSlaRow row : rows) {
            TypeSlaMeta meta = typeByCode.get(row.typeCode());
            Integer hours = meta == null ? null : meta.slaTargetHours();
            Duration elapsed = SlaClock.elapsed(row.createdAt(), row.closedAt(), row.updatedAt(), now, row.closed());
            SlaStatus status = SlaStatus.of(elapsed, hours, row.closed());
            if (status == null) {
                unscored++;
            } else {
                switch (status) {
                    case ON_TRACK -> onTrack++;
                    case AT_RISK -> atRisk++;
                    case BREACHED -> breached++;
                }
            }
        }
        String label = GROUP_BY_TYPE.equals(groupBy)
                ? typeByCode.containsKey(key) ? typeByCode.get(key).label() : key
                : key;
        return new SlaSummaryBucket(
                key,
                label,
                rows.size(),
                onTrack,
                atRisk,
                breached,
                unscored,
                meanHours(rows, now),
                medianHours(rows, now));
    }

    private static Double meanHours(List<DossierSlaRow> rows, Instant now) {
        if (rows.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (DossierSlaRow row : rows) {
            sum += elapsed(row, now).toMillis() / 3_600_000.0;
        }
        return round1(sum / rows.size());
    }

    private static Double medianHours(List<DossierSlaRow> rows, Instant now) {
        if (rows.isEmpty()) {
            return null;
        }
        List<Long> millis = rows.stream().map(row -> elapsed(row, now).toMillis()).sorted().toList();
        int n = millis.size();
        long mid = n % 2 == 1 ? millis.get(n / 2) : (millis.get(n / 2 - 1) + millis.get(n / 2)) / 2;
        return round1(mid / 3_600_000.0);
    }

    private static Duration elapsed(DossierSlaRow row, Instant now) {
        return SlaClock.elapsed(row.createdAt(), row.closedAt(), row.updatedAt(), now, row.closed());
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
