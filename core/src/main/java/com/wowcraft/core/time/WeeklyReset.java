package com.wowcraft.core.time;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/** Week numbering for lockouts, the Great Vault and affix rotation (default: Wednesday 07:00 UTC, like EU/US WoW). */
public final class WeeklyReset {
    private final DayOfWeek day;
    private final int hour;
    private static final ZonedDateTime EPOCH = ZonedDateTime.of(2024, 1, 3, 7, 0, 0, 0, ZoneOffset.UTC); // a Wednesday

    public WeeklyReset(DayOfWeek day, int hour) {
        this.day = day;
        this.hour = hour;
    }

    public static WeeklyReset standard() {
        return new WeeklyReset(DayOfWeek.WEDNESDAY, 7);
    }

    /** Week index for a moment (0 = the week starting at the epoch reset). */
    public long weekIndex(Instant now) {
        ZonedDateTime anchor = EPOCH.with(TemporalAdjusters.nextOrSame(day)).withHour(hour);
        long days = ChronoUnit.DAYS.between(anchor.toLocalDate(), now.atZone(ZoneOffset.UTC).toLocalDate());
        ZonedDateTime t = now.atZone(ZoneOffset.UTC);
        long weeks = Math.floorDiv(days, 7);
        // before the reset hour on reset day we're still in the previous week
        ZonedDateTime weekStart = anchor.plusWeeks(weeks);
        if (t.isBefore(weekStart)) weeks--;
        return weeks;
    }

    public long currentWeek() {
        return weekIndex(Instant.now());
    }

    /** Seconds until the next reset. */
    public long secondsUntilReset(Instant now) {
        ZonedDateTime anchor = EPOCH.with(TemporalAdjusters.nextOrSame(day)).withHour(hour);
        long w = weekIndex(now);
        ZonedDateTime next = anchor.plusWeeks(w + 1);
        return Math.max(0, ChronoUnit.SECONDS.between(now.atZone(ZoneOffset.UTC), next));
    }
}
