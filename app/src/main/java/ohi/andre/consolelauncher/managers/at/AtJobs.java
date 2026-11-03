package ohi.andre.consolelauncher.managers.at;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.managers.status.StatusFormats;

/**
 * Commands scheduled with "at": when they run, and saving them. Plain Java, so it can be unit tested.
 *
 *   at 22:00 theme -apply oled       at 7:30pm ...      at 7pm ...
 *   at +30m flash                    at 2h ...          at 1h30m ...
 *   at daily 22:00 theme -apply oled
 */
public class AtJobs {

    private AtJobs() {}

    public static class Job {
        public final int id;
        public final long at;
        public final boolean daily;
        /** For daily jobs: the time of day. */
        public final int hour, minute;
        public final String command;

        public Job(int id, long at, boolean daily, int hour, int minute, String command) {
            this.id = id;
            this.at = at;
            this.daily = daily;
            this.hour = hour;
            this.minute = minute;
            this.command = command;
        }

        /** The same job, moved to its next time (daily jobs). */
        public Job next(long now, TimeZone zone) {
            return new Job(id, nextTimeOfDay(hour, minute, now + 60_000, zone), daily, hour, minute, command);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("at", at).put("daily", daily)
                    .put("hour", hour).put("minute", minute).put("command", command);
        }

        static Job fromJson(JSONObject o) {
            return new Job(o.optInt("id"), o.optLong("at"), o.optBoolean("daily"), o.optInt("hour"), o.optInt("minute"), o.optString("command"));
        }
    }

    /** What was typed after "at": a new job, or the reason it couldn't be read. */
    public static class Parsed {
        public final Job job;
        public final String error;

        Parsed(Job job, String error) {
            this.job = job;
            this.error = error;
        }
    }

    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?\\s*([ap]\\.?m\\.?)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE = Pattern.compile("^\\+?(?:(\\d{1,3})h)?(?:(\\d{1,4})m)?$", Pattern.CASE_INSENSITIVE);

    /**
     * Reads "22:00 theme -apply oled", "daily 7:30pm cal", "+30m flash", ...
     * A clock time that already passed today means tomorrow.
     */
    public static Parsed parse(String input, int id, long now, TimeZone zone) {
        String s = input == null ? "" : input.trim();
        boolean daily = false;
        if (s.toLowerCase(Locale.ROOT).startsWith("daily ")) {
            daily = true;
            s = s.substring(6).trim();
        }

//        the time is the first word, or the first two when the second is am/pm ("7:30 pm")
        String[] words = s.split("\\s+", 3);
        if (s.length() == 0) return new Parsed(null, "Say when and what, like: at 22:00 theme -apply oled");
        if (words.length < 2) return new Parsed(null, "What should run at " + s + "? Like: at " + s + " flash");
        String when = words[0];
        String rest = s.substring(words[0].length()).trim();
        if (words[1].matches("(?i)[ap]\\.?m\\.?") && words.length >= 2) {
            when = words[0] + words[1];
            rest = words.length == 3 ? words[2] : "";
        }
        if (rest.length() == 0) return new Parsed(null, "What should run at " + when + "? Like: at " + when + " flash");

        Matcher clock = CLOCK.matcher(when);
        if (clock.matches() && (clock.group(2) != null || clock.group(3) != null)) {
            int h = Integer.parseInt(clock.group(1));
            int m = clock.group(2) != null ? Integer.parseInt(clock.group(2)) : 0;
            String ampm = clock.group(3);
            if (ampm != null) {
                if (h < 1 || h > 12) return new Parsed(null, "That isn't a time: " + when);
                boolean pm = ampm.toLowerCase(Locale.ROOT).startsWith("p");
                h = h % 12 + (pm ? 12 : 0);
            }
            if (h > 23 || m > 59) return new Parsed(null, "That isn't a time: " + when);
            return new Parsed(new Job(id, nextTimeOfDay(h, m, now, zone), daily, h, m, rest), null);
        }

        Matcher rel = RELATIVE.matcher(when);
        if (rel.matches() && (rel.group(1) != null || rel.group(2) != null)) {
            if (daily) return new Parsed(null, "Daily jobs need a time of day, like: at daily 22:00 ...");
            long minutes = (rel.group(1) != null ? Long.parseLong(rel.group(1)) * 60 : 0)
                    + (rel.group(2) != null ? Long.parseLong(rel.group(2)) : 0);
            if (minutes < 1) return new Parsed(null, "Pick at least a minute from now");
            long at = now + minutes * 60_000;
            Calendar c = Calendar.getInstance(zone, Locale.US);
            c.setTimeInMillis(at);
            return new Parsed(new Job(id, at, false, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), rest), null);
        }

        return new Parsed(null, "That isn't a time: " + when + ". Use 22:00, 7:30pm, 7pm, +30m or 2h");
    }

    /** The next moment it's hour:minute, at least a few seconds from now. */
    public static long nextTimeOfDay(int hour, int minute, long now, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone, Locale.US);
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now + 5_000) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    public static String toJson(List<Job> jobs) {
        JSONArray a = new JSONArray();
        try {
            for (Job j : jobs) a.put(j.toJson());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return a.toString();
    }

    public static List<Job> fromJson(String json) {
        List<Job> out = new ArrayList<>();
        if (json == null || json.length() == 0) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) out.add(Job.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static int nextId(List<Job> jobs) {
        int max = 0;
        for (Job j : jobs) max = Math.max(max, j.id);
        return max + 1;
    }

    /** One line per job: "1  22:00 daily  theme -apply oled  (in 3h 2m)". */
    public static String list(List<Job> jobs, long now, TimeZone zone, boolean use24h) {
        if (jobs.isEmpty()) return "Nothing scheduled. Example: at 22:00 theme -apply oled";
        SimpleDateFormat time = new SimpleDateFormat(use24h ? "HH:mm" : "h:mm a", Locale.US);
        time.setTimeZone(zone);
        SimpleDateFormat day = new SimpleDateFormat("EEE ", Locale.US);
        day.setTimeZone(zone);

        StringBuilder b = new StringBuilder();
        for (Job j : jobs) {
            if (b.length() > 0) b.append('\n');
            boolean later = j.at - now > 24 * 3_600_000L;
            b.append(j.id).append("  ").append(later ? day.format(new Date(j.at)) : "").append(time.format(new Date(j.at)))
                    .append(j.daily ? " daily" : "").append("  ").append(j.command)
                    .append("  (in ").append(StatusFormats.duration(j.at - now)).append(')');
        }
        return b.append("\nCancel one with: at cancel [number]").toString();
    }
}
