package be.myrmacta.domain.deadline.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO §120 (D-CAL-HOLIDAYS) — les dix jours fériés légaux fédéraux belges, en dates concrètes.
 * Pâques suit le comput grégorien (Meeus).
 */
public final class BelgianFederalHolidays {

    private BelgianFederalHolidays() {}

    public static List<LocalDate> of(int year) {
        LocalDate easter = easterSunday(year);
        return List.of(
                LocalDate.of(year, 1, 1),
                easter.plusDays(1),
                LocalDate.of(year, 5, 1),
                easter.plusDays(39),
                easter.plusDays(50),
                LocalDate.of(year, 7, 21),
                LocalDate.of(year, 8, 15),
                LocalDate.of(year, 11, 1),
                LocalDate.of(year, 11, 11),
                LocalDate.of(year, 12, 25));
    }

    /** Libellés français, dans l'ordre de {@link #of(int)}. */
    private static final List<String> LABELS = List.of("Jour de l'an", "Lundi de Pâques", "Fête du Travail", "Ascension",
            "Lundi de Pentecôte", "Fête nationale", "Assomption", "Toussaint", "Armistice", "Noël");

    /** TODO §121 (D-CAL-BE-SOURCE) — les fériés des deux années, en jours fermés libellés. */
    public static List<CalendarEntry> entries(int year, int otherYear) {
        List<CalendarEntry> entries = new ArrayList<>();
        for (int y : new int[] {year, otherYear}) {
            List<LocalDate> dates = of(y);
            for (int i = 0; i < dates.size(); i++) {
                entries.add(CalendarEntry.day(dates.get(i), CalendarEntry.Nature.CLOSED, LABELS.get(i)));
            }
        }
        return List.copyOf(entries);
    }

    public static List<LocalDate> of(int year, int otherYear) {
        List<LocalDate> dates = new ArrayList<>(of(year));
        dates.addAll(of(otherYear));
        return List.copyOf(dates);
    }

    /** Dimanche de Pâques, algorithme grégorien anonyme. */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
