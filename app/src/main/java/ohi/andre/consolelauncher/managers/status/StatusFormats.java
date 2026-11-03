package ohi.andre.consolelauncher.managers.status;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import ohi.andre.consolelauncher.tuils.StatusBars;

/**
 * The text of the "next alarm" and "now playing" lines at the top of the home screen.
 * Plain Java, so it can be unit tested.
 */
public class StatusFormats {

    private StatusFormats() {}

//    ---------------------------------------------------------------- next alarm

    /**
     * Codes: %t time (6:45 AM or 06:45, like the phone's clock), %day (today, tomorrow, or Wed),
     * %date (Nov 4), %in (time left: 7h 20m), %app (the app that set it).
     *
     * @param triggerMillis when the alarm rings, or 0 or less if there's none (returns "")
     */
    public static String alarm(String format, long triggerMillis, long nowMillis, TimeZone zone, boolean use24h, String app) {
        if (triggerMillis <= 0 || format == null) return "";

        SimpleDateFormat time = new SimpleDateFormat(use24h ? "HH:mm" : "h:mm a", Locale.US);
        SimpleDateFormat date = new SimpleDateFormat("MMM d", Locale.US);
        SimpleDateFormat weekday = new SimpleDateFormat("EEE", Locale.US);
        time.setTimeZone(zone);
        date.setTimeZone(zone);
        weekday.setTimeZone(zone);
        Date when = new Date(triggerMillis);

        int days = daysBetween(nowMillis, triggerMillis, zone);
        String day = days == 0 ? "today" : days == 1 ? "tomorrow" : weekday.format(when);

        return format
                .replace("%day", day)
                .replace("%date", date.format(when))
                .replace("%in", duration(triggerMillis - nowMillis))
                .replace("%app", app == null ? "" : app)
                .replace("%t", time.format(when));
    }

    /** Calendar days from one moment to another in the given time zone (0 = same day). */
    static int daysBetween(long fromMillis, long toMillis, TimeZone zone) {
        Calendar a = Calendar.getInstance(zone, Locale.US);
        a.setTimeInMillis(fromMillis);
        Calendar b = Calendar.getInstance(zone, Locale.US);
        b.setTimeInMillis(toMillis);
        int days = 0;
        while (a.get(Calendar.YEAR) < b.get(Calendar.YEAR) || a.get(Calendar.DAY_OF_YEAR) < b.get(Calendar.DAY_OF_YEAR)) {
            a.add(Calendar.DAY_OF_YEAR, 1);
            if (++days > 400) break;
        }
        return days;
    }

    /** "7h 20m", "45m", "2d 3h". Rounds up to the next minute, like a clock app does. */
    public static String duration(long millis) {
        long minutes = Math.max(0, (millis + 59_999) / 60_000);
        long d = minutes / 1440, h = (minutes % 1440) / 60, m = minutes % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

//    ---------------------------------------------------------------- now playing

    public static final String PLAYING = "►", PAUSED = "||";

    /**
     * Codes: %title, %artist, %album, %app, %state (► or ||), %pos and %dur (3:05),
     * %bar (how far into the song, see StatusBars). Missing values are left out.
     *
     * @param title null when nothing is playing (returns "")
     */
    public static String nowPlaying(String format, String title, String artist, String album, String app,
                                    boolean playing, long positionMillis, long durationMillis) {
        if (title == null || title.length() == 0 || format == null) return "";

        double percent = durationMillis > 0 ? positionMillis * 100.0 / durationMillis : Double.NaN;
        String out = StatusBars.replaceCode(format, "bar", percent);
        out = out
                .replace("%title", title)
                .replace("%artist", artist == null ? "" : artist)
                .replace("%album", album == null ? "" : album)
                .replace("%app", app == null ? "" : app)
                .replace("%state", playing ? PLAYING : PAUSED)
                .replace("%pos", positionMillis >= 0 ? clock(positionMillis) : "")
                .replace("%dur", durationMillis > 0 ? clock(durationMillis) : "");

//        a song without an artist shouldn't end with a dangling " — "
        return out.replaceAll("\\s+[—-]\\s*$", "").replaceAll("\\s+[—-]\\s+[—-]\\s+", " — ").trim();
    }

    /** 3:05, or 1:02:09 for long tracks. */
    public static String clock(long millis) {
        long s = Math.max(0, millis / 1000);
        if (s >= 3600) return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
