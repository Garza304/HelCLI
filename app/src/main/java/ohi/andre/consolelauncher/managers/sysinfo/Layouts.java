package ohi.andre.consolelauncher.managers.sysinfo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Text layouts for "sysinfo" and "cal". Plain Java, so they can be unit tested; the commands add colors.
 */
public class Layouts {

    private Layouts() {}

    /** Characters that fit a line in the terminal at the default text size on a phone. */
    public static final int WIDTH = 44;

//    ---------------------------------------------------------------- sysinfo

    public static final String[] LOGO = {
            "┌─────────┐",
            "│ >_      │",
            "│         │",
            "│  HelCLI │",
            "│         │",
            "│         │",
            "├─────────┤",
            "│    o    │",
            "└─────────┘",
    };
    public static final int LOGO_WIDTH = 11, GAP = 2;

    /** One line of the sysinfo card: the logo part, then the label and value of an info row (any can be ""). */
    public static class Row {
        public final String logo, label, value;

        Row(String logo, String label, String value) {
            this.logo = logo;
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return logo + label + value;
        }
    }

    /**
     * Puts the logo beside the info, neofetch style. The first entry of info is the title
     * (user@device); a rule goes under it. Values that don't fit are shortened with "…".
     *
     * @param info pairs of label and value
     */
    public static List<Row> sysinfo(String title, List<String[]> info) {
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[] {"", title});
        lines.add(new String[] {"", repeat('-', Math.min(title.length(), WIDTH - LOGO_WIDTH - GAP))});
        lines.addAll(info);

        int room = WIDTH - LOGO_WIDTH - GAP;
        List<Row> out = new ArrayList<>();
        int n = Math.max(LOGO.length, lines.size());
        for (int i = 0; i < n; i++) {
            String logo = i < LOGO.length ? LOGO[i] : repeat(' ', LOGO_WIDTH);
            logo += repeat(' ', GAP);
            String label = "", value = "";
            if (i < lines.size()) {
                String[] l = lines.get(i);
                label = l[0].length() > 0 ? l[0] + ": " : "";
                value = shorten(l[1] == null ? "" : l[1], room - label.length());
            }
            out.add(new Row(logo, label, value));
        }
        return out;
    }

    static String shorten(String s, int max) {
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }

    /** "3d 4h 12m" from milliseconds. */
    public static String uptime(long millis) {
        long m = millis / 60000, d = m / 1440, h = (m % 1440) / 60;
        m %= 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

//    ---------------------------------------------------------------- cal

    /** One calendar event. Times are milliseconds; all-day events use UTC midnights, as Android stores them. */
    public static class Event {
        public final String title;
        public final long begin, end;
        public final boolean allDay;
        public final int color;

        public Event(String title, long begin, long end, boolean allDay, int color) {
            this.title = title == null || title.trim().length() == 0 ? "(no title)" : title.trim();
            this.begin = begin;
            this.end = end;
            this.allDay = allDay;
            this.color = color;
        }
    }

    /** One line of the agenda: a day heading, or an event with a colored marker before it. */
    public static class AgendaLine {
        public final String text;
        public final boolean heading;
        /** The event's calendar color, for the marker. 0 for headings. */
        public final int color;

        AgendaLine(String text, boolean heading, int color) {
            this.text = text;
            this.heading = heading;
            this.color = color;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public static final String MARKER = "■ ";

    /**
     * Groups events by day: "Today · Tue Nov 3", then "  ■ 9:00 AM  Standup".
     * Events running right now say "now". Days without events are skipped.
     */
    public static List<AgendaLine> agenda(List<Event> events, long now, int days, TimeZone zone, boolean use24h) {
        return agenda(events, now, 0, days, zone, use24h);
    }

    /** @param firstDay 0 starts today, 1 starts tomorrow */
    public static List<AgendaLine> agenda(List<Event> events, long now, int firstDay, int days, TimeZone zone, boolean use24h) {
        List<AgendaLine> out = new ArrayList<>();
        SimpleDateFormat dayName = new SimpleDateFormat("EEE MMM d", Locale.US);
        dayName.setTimeZone(zone);
        SimpleDateFormat time = new SimpleDateFormat(use24h ? "HH:mm" : "h:mm a", Locale.US);
        time.setTimeZone(zone);

        Calendar start = Calendar.getInstance(zone, Locale.US);
        start.setTimeInMillis(now);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        start.add(Calendar.DAY_OF_YEAR, firstDay);

        int timeWidth = use24h ? 5 : 8;
        for (int d = firstDay; d < firstDay + days; d++) {
            long dayStart = start.getTimeInMillis();
            Calendar next = (Calendar) start.clone();
            next.add(Calendar.DAY_OF_YEAR, 1);
            long dayEnd = next.getTimeInMillis();

            List<AgendaLine> lines = new ArrayList<>();
            for (Event e : events) {
                long b = e.begin, en = e.end;
                if (e.allDay) {
//                    all-day events are stored as UTC dates: move them to the same date here
                    b = sameDateIn(e.begin, zone);
                    en = sameDateIn(e.end, zone);
                }
                if (b >= dayEnd || en <= dayStart || (en == b && b < dayStart)) continue;

                String when;
                if (e.allDay) when = pad("all day", timeWidth);
                else if (b <= now && now < en) when = pad("now", timeWidth);
                else if (b < dayStart) when = pad("cont.", timeWidth);
                else when = pad(time.format(new Date(b)), timeWidth);

                String text = "  " + MARKER + when + "  " + e.title;
                lines.add(new AgendaLine(Breakdown.pad(text, Math.min(text.length(), WIDTH)), false, e.color));
            }

            if (!lines.isEmpty()) {
                String label = d == 0 ? "Today · " : d == 1 ? "Tomorrow · " : "";
                out.add(new AgendaLine(label + dayName.format(new Date(dayStart)), true, 0));
                out.addAll(lines);
            }
            start = next;
        }
        return out;
    }

    /** The UTC date of an all-day event, as midnight of that date in the given zone. */
    static long sameDateIn(long utcMillis, TimeZone zone) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance(zone, Locale.US);
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return local.getTimeInMillis();
    }

    private static String pad(String s, int w) {
        return Breakdown.pad(s, Math.max(w, s.length()));
    }
}
