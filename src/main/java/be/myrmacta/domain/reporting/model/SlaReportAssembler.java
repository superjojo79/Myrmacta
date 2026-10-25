package be.myrmacta.domain.reporting.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SlaReportAssembler {

    private SlaReportAssembler() {}

    public static SlaReport assemble(
            Instant from,
            Instant to,
            int complianceTargetPercent,
            List<TypeSlaMeta> types,
            List<DossierSlaRow> current,
            List<DossierSlaRow> previous,
            Map<String, Long> producedDocsByType,
            List<ActiveTaskRow> activeTasks,
            List<HistoricStepRow> steps,
            Instant now) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(now, "now");
        Map<String, TypeSlaMeta> typeByCode = types == null
                ? Map.of()
                : types.stream().collect(Collectors.toMap(TypeSlaMeta::code, t -> t, (a, b) -> a));
        List<DossierSlaRow> currentRows = current == null ? List.of() : current;
        List<DossierSlaRow> previousRows = previous == null ? List.of() : previous;
        Map<String, Long> produced = producedDocsByType == null ? Map.of() : producedDocsByType;

        Double compliance = compliancePercent(currentRows, typeByCode, now);
        long overdue = currentRows.stream()
                .filter(row -> overdue(row, typeByCode.get(row.typeCode()), now))
                .count();
        Double mean = meanDays(currentRows, now);
        Double median = medianDays(currentRows, now);
        Double previousMean = meanDays(previousRows, now);
        Double delta = mean == null || previousMean == null ? null : round1(mean - previousMean);

        return new SlaReport(
                from,
                to,
                complianceTargetPercent,
                compliance,
                overdue,
                mean,
                median,
                delta,
                typeShares(currentRows, typeByCode),
                stepDurations(steps == null ? List.of() : steps),
                managerLoads(activeTasks == null ? List.of() : activeTasks, typeByCode, now),
                typeBreakdowns(currentRows, previousRows, typeByCode, produced, now));
    }

    private static Double compliancePercent(
            List<DossierSlaRow> rows, Map<String, TypeSlaMeta> types, Instant now) {
        long scored = 0;
        long met = 0;
        for (DossierSlaRow row : rows) {
            TypeSlaMeta type = types.get(row.typeCode());
            if (type == null || type.slaTargetHours() == null) {
                continue;
            }
            scored++;
            if (!overdue(row, type, now)) {
                met++;
            }
        }
        if (scored == 0) {
            return null;
        }
        return round1(met * 100.0 / scored);
    }

    private static boolean overdue(DossierSlaRow row, TypeSlaMeta type, Instant now) {
        if (type == null || type.slaTargetHours() == null) {
            return false;
        }
        Duration elapsed = SlaClock.elapsed(row.createdAt(), row.closedAt(), row.updatedAt(), now, row.closed());
        return SlaClock.overdue(elapsed, type.slaTargetHours());
    }

    private static Double meanDays(List<DossierSlaRow> rows, Instant now) {
        if (rows.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (DossierSlaRow row : rows) {
            sum += toDays(elapsed(row, now));
        }
        return round1(sum / rows.size());
    }

    private static Double medianDays(List<DossierSlaRow> rows, Instant now) {
        if (rows.isEmpty()) {
            return null;
        }
        List<Long> millis = rows.stream()
                .map(row -> elapsed(row, now).toMillis())
                .sorted()
                .toList();
        int n = millis.size();
        long mid;
        if (n % 2 == 1) {
            mid = millis.get(n / 2);
        } else {
            mid = (millis.get(n / 2 - 1) + millis.get(n / 2)) / 2;
        }
        return round1(mid / 86_400_000.0);
    }

    private static Duration elapsed(DossierSlaRow row, Instant now) {
        return SlaClock.elapsed(row.createdAt(), row.closedAt(), row.updatedAt(), now, row.closed());
    }

    private static List<SlaReport.TypeShare> typeShares(
            List<DossierSlaRow> rows, Map<String, TypeSlaMeta> types) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Long> volumes = countByType(rows);
        List<Map.Entry<String, Long>> ordered = volumes.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();
        long total = rows.size();
        List<SlaReport.TypeShare> shares = new ArrayList<>();
        double remaining = 100.0;
        for (int i = 0; i < ordered.size(); i++) {
            Map.Entry<String, Long> entry = ordered.get(i);
            double percent = i == ordered.size() - 1 ? round1(remaining) : round1(entry.getValue() * 100.0 / total);
            remaining -= percent;
            TypeSlaMeta meta = types.get(entry.getKey());
            String label = meta == null ? entry.getKey() : meta.label();
            shares.add(new SlaReport.TypeShare(entry.getKey(), label, entry.getValue(), percent));
        }
        return List.copyOf(shares);
    }

    private static List<SlaReport.StepDuration> stepDurations(List<HistoricStepRow> steps) {
        Map<String, List<Long>> byName = new LinkedHashMap<>();
        for (HistoricStepRow step : steps) {
            byName.computeIfAbsent(step.activityName(), key -> new ArrayList<>()).add(step.durationMillis());
        }
        return byName.entrySet().stream()
                .map(entry -> {
                    double meanMs = entry.getValue().stream().mapToLong(Long::longValue).average().orElse(0);
                    return new SlaReport.StepDuration(entry.getKey(), round1(meanMs / 3_600_000.0));
                })
                .sorted(Comparator.comparingDouble(SlaReport.StepDuration::meanHours).reversed())
                .toList();
    }

    private static List<SlaReport.ManagerLoad> managerLoads(
            List<ActiveTaskRow> tasks, Map<String, TypeSlaMeta> types, Instant now) {
        record Acc(long inProgress, long overdue) {}
        Map<String, Acc> byAssignee = new HashMap<>();
        for (ActiveTaskRow task : tasks) {
            String key = task.assigneeId() == null || task.assigneeId().isBlank() ? "" : task.assigneeId();
            Acc acc = byAssignee.getOrDefault(key, new Acc(0, 0));
            boolean overdue = task.dossier() != null && overdue(task.dossier(), types.get(task.typeCode()), now);
            byAssignee.put(key, new Acc(acc.inProgress + 1, acc.overdue + (overdue ? 1 : 0)));
        }
        List<SlaReport.ManagerLoad> loads = new ArrayList<>();
        Acc unassigned = byAssignee.remove("");
        byAssignee.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Acc>>comparingLong(e -> e.getValue().inProgress)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> {
                    Acc acc = entry.getValue();
                    Double sla = acc.inProgress == 0
                            ? null
                            : round1((acc.inProgress - acc.overdue) * 100.0 / acc.inProgress);
                    loads.add(new SlaReport.ManagerLoad(
                            entry.getKey(), entry.getKey(), acc.inProgress, acc.overdue, sla));
                });
        if (unassigned != null) {
            loads.add(new SlaReport.ManagerLoad(
                    null, "Non assignés", unassigned.inProgress, unassigned.overdue, null));
        }
        return List.copyOf(loads);
    }

    private static List<SlaReport.TypeBreakdown> typeBreakdowns(
            List<DossierSlaRow> current,
            List<DossierSlaRow> previous,
            Map<String, TypeSlaMeta> types,
            Map<String, Long> produced,
            Instant now) {
        Map<String, Long> currentVol = countByType(current);
        Map<String, Long> previousVol = countByType(previous);
        return currentVol.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> {
                    String code = entry.getKey();
                    TypeSlaMeta meta = types.get(code);
                    String label = meta == null ? code : meta.label();
                    String workflow = meta == null ? "" : meta.workflowKey();
                    List<DossierSlaRow> ofType = current.stream().filter(row -> row.typeCode().equals(code)).toList();
                    Double mean = meanDays(ofType, now);
                    Double sla = compliancePercent(ofType, types, now);
                    long prev = previousVol.getOrDefault(code, 0L);
                    return new SlaReport.TypeBreakdown(
                            code,
                            label,
                            workflow,
                            entry.getValue(),
                            mean,
                            sla,
                            produced.getOrDefault(code, 0L),
                            trendPercent(entry.getValue(), prev));
                })
                .toList();
    }

    static Double trendPercent(long current, long previous) {
        if (previous == 0 && current == 0) {
            return 0.0;
        }
        if (previous == 0) {
            return 100.0;
        }
        double raw = (current - previous) * 100.0 / previous;
        if (Math.abs(raw) < 0.5) {
            return 0.0;
        }
        return round1(raw);
    }

    private static Map<String, Long> countByType(List<DossierSlaRow> rows) {
        Map<String, Long> volumes = new HashMap<>();
        for (DossierSlaRow row : rows) {
            volumes.merge(row.typeCode(), 1L, Long::sum);
        }
        return volumes;
    }

    private static double toDays(Duration duration) {
        return duration.toMillis() / 86_400_000.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public static Double remainingHoursFor(DossierSlaRow row, Integer slaTargetHours, Instant now) {
        Duration elapsed = elapsed(row, now);
        return SlaClock.remainingHours(elapsed, slaTargetHours);
    }
}
