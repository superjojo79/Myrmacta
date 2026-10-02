package com.flowforge.domain.workflow.draft;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO §82 (D6) — écart indicatif entre deux documents XML : lignes (rognées, non vides) ajoutées et retirées, par différence de
 * multi-ensembles. Ce n'est pas un diff ordonné : il dit « de l'ordre de N lignes changent », pas où.
 */
public final class LineDiff {

    private LineDiff() {}

    public record Summary(int added, int removed) {}

    public static Summary summary(String published, String draft) {
        Map<String, Integer> counts = new HashMap<>();
        if (published != null) {
            for (String line : published.split("\\R")) {
                String t = line.strip();
                if (!t.isEmpty()) {
                    counts.merge(t, 1, Integer::sum);
                }
            }
        }
        int added = 0;
        if (draft != null) {
            for (String line : draft.split("\\R")) {
                String t = line.strip();
                if (t.isEmpty()) {
                    continue;
                }
                Integer remaining = counts.get(t);
                if (remaining == null || remaining == 0) {
                    added++;
                } else {
                    counts.put(t, remaining - 1);
                }
            }
        }
        int removed = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new Summary(added, removed);
    }
}
