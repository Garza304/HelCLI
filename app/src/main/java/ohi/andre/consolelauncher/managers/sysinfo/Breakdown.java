package ohi.andre.consolelauncher.managers.sysinfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ohi.andre.consolelauncher.tuils.StatusBars;

/**
 * The text of the "ram" and "storage" commands. Plain Java, so it can be unit tested; the commands
 * collect the numbers from Android and hand them here.
 */
public class Breakdown {

    private Breakdown() {}

    public static final int BAR_WIDTH = 12;
    /** The big bar under each heading. Rows are 37 characters, so the output fits a phone's terminal. */
    public static final int HEADER_BAR_WIDTH = 37;

//    ---------------------------------------------------------------- sizes

    /** "7.6 GB", "850 MB". RAM is counted in 1024s, like Linux; storage in 1000s, like Android's Settings. */
    public static String size(long bytes, boolean binary) {
        double unit = binary ? 1024 : 1000;
        String[] names = {"B", "KB", "MB", "GB", "TB"};
        double v = Math.max(0, bytes);
        int i = 0;
        while (v >= unit && i < names.length - 1) {
            v /= unit;
            i++;
        }
        if (i == 0) return (long) v + " B";
        return String.format(Locale.US, v >= 100 ? "%.0f %s" : v >= 10 ? "%.1f %s" : "%.2f %s", v, names[i]);
    }

    static int percent(long part, long whole) {
        return whole <= 0 ? 0 : (int) Math.round(part * 100.0 / whole);
    }

    /** One aligned row, 37 characters: "  Cached        1.90 GB  [██░░░░░░░░]". */
    static String row(String label, long bytes, long whole, boolean binary, String style) {
        return "  " + pad(label, 14) + padLeft(size(bytes, binary), 9) + "  " + StatusBars.bar(percent(bytes, whole), BAR_WIDTH, style);
    }

    static String pad(String s, int width) {
        if (s.length() > width) return s.substring(0, width - 1) + "…";
        StringBuilder b = new StringBuilder(s);
        while (b.length() < width) b.append(' ');
        return b.toString();
    }

    static String padLeft(String s, int width) {
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < width; i++) b.append(' ');
        return b.append(s).toString();
    }

//    ---------------------------------------------------------------- RAM

    /** Reads /proc/meminfo ("MemTotal:  7812345 kB" lines) into bytes. */
    public static Map<String, Long> parseMeminfo(String text) {
        Map<String, Long> out = new HashMap<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String[] value = line.substring(colon + 1).trim().split("\\s+");
            try {
                long n = Long.parseLong(value[0]);
                if (value.length > 1 && value[1].equalsIgnoreCase("kB")) n *= 1024;
                out.put(line.substring(0, colon).trim(), n);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** Numbers for the RAM report. Use -1 for anything unknown. */
    public static class Ram {
        public long total = -1, available = -1, free = -1, swapTotal = -1, swapFree = -1;
        /** Android closes background apps when available memory drops below this. */
        public long threshold = -1;
        /** The size the phone is sold as (8 GB), Android 14 and later. */
        public long advertised = -1;
        public boolean low;
        /** HelCLI's own memory. */
        public long self = -1;

        /** Fills in what /proc/meminfo knows. MemAvailable is missing on very old kernels. */
        public Ram fromMeminfo(Map<String, Long> m) {
            if (m.containsKey("MemTotal")) total = m.get("MemTotal");
            if (m.containsKey("MemFree")) free = m.get("MemFree");
            if (m.containsKey("MemAvailable")) {
                available = m.get("MemAvailable");
            } else if (free >= 0) {
                available = free + get(m, "Buffers") + get(m, "Cached");
            }
            if (m.containsKey("SwapTotal")) swapTotal = m.get("SwapTotal");
            if (m.containsKey("SwapFree")) swapFree = m.get("SwapFree");
            return this;
        }

        private static long get(Map<String, Long> m, String k) {
            Long v = m.get(k);
            return v == null ? 0 : v;
        }
    }

    public static String ram(Ram r, String style) {
        if (r.total <= 0 || r.available < 0) return "Couldn't read the memory information";

        long used = Math.max(0, r.total - r.available);
        StringBuilder b = new StringBuilder();
        b.append("RAM: ").append(size(used, true)).append(" of ").append(size(r.total, true))
                .append(" in use (").append(percent(used, r.total)).append("%)");
        b.append('\n').append(StatusBars.bar(percent(used, r.total), HEADER_BAR_WIDTH, style)).append('\n');

        boolean cached = r.free >= 0 && r.free <= r.available;
        b.append('\n').append(row("In use", used, r.total, true, style));
        if (cached) {
            b.append('\n').append(row("Cached", r.available - r.free, r.total, true, style));
            b.append('\n').append(row("Free", r.free, r.total, true, style));
        } else {
            b.append('\n').append(row("Available", r.available, r.total, true, style));
        }

        if (r.swapTotal > 0 && r.swapFree >= 0) {
            long swapUsed = Math.max(0, r.swapTotal - r.swapFree);
            b.append('\n').append(row("Swap (zram)", swapUsed, r.swapTotal, true, style));
        }

        b.append("\n\n");
        if (cached) b.append("Cached memory is freed when apps need it.\n");
        if (r.swapTotal > 0 && r.swapFree >= 0) {
            b.append("Swap is compressed memory: ").append(size(r.swapTotal, true)).append(" in all.\n");
        }
        if (r.advertised > 0) {
            b.append("This phone has ").append(size(r.advertised, false)).append(" of RAM; ")
                    .append(size(r.total, true)).append(" is usable.\n");
        }
        if (r.low) {
            b.append("Memory is low: Android is closing background apps.");
        } else if (r.threshold > 0) {
            b.append("Android starts closing background apps below ").append(size(r.threshold, true))
                    .append(" available. ").append(size(r.available, true)).append(" is available now.");
        } else {
            b.append(size(r.available, true)).append(" is available now.");
        }
        if (r.self > 0) b.append("\nHelCLI itself is using ").append(size(r.self, true)).append('.');
        return b.toString();
    }

//    ---------------------------------------------------------------- storage

    /** One app's storage. */
    public static class App {
        public final String name;
        public final long app, data, cache;

        public App(String name, long app, long data, long cache) {
            this.name = name;
            this.app = app;
            this.data = data;
            this.cache = cache;
        }

        /** Data already includes the cache, as Android reports it. */
        public long total() {
            return app + data;
        }
    }

    /** A storage volume, like the internal storage or an SD card. Use -1 for unknown media sizes. */
    public static class Volume {
        public final String name;
        public final long total, free;
        public long images = -1, videos = -1, audio = -1, appFiles = -1, otherFiles = -1;

        public Volume(String name, long total, long free) {
            this.name = name;
            this.total = total;
            this.free = free;
        }

        public long used() {
            return Math.max(0, total - free);
        }
    }

    /**
     * @param apps every app's size, or null when HelCLI can't see them (no Usage access)
     * @param top  how many of the biggest apps to list
     */
    public static String storage(Volume internal, List<Volume> others, List<App> apps, int top, String style) {
        StringBuilder b = new StringBuilder();
        if (internal == null || internal.total <= 0) {
            b.append("Couldn't read the internal storage");
        } else {
            b.append("Storage: ").append(size(internal.used(), false)).append(" of ").append(size(internal.total, false))
                    .append(" used (").append(percent(internal.used(), internal.total)).append("%)")
                    .append('\n').append(StatusBars.bar(percent(internal.used(), internal.total), HEADER_BAR_WIDTH, style)).append('\n');

            long appsTotal = 0;
            if (apps != null) for (App a : apps) appsTotal += a.total();

            boolean media = internal.images >= 0;
            if (apps != null || media) b.append('\n');
            if (apps != null) b.append(row("Apps", appsTotal, internal.total, false, style)).append('\n');
            long known = appsTotal;
            if (media) {
                b.append(row("Images", internal.images, internal.total, false, style)).append('\n');
                b.append(row("Videos", internal.videos, internal.total, false, style)).append('\n');
                b.append(row("Audio", internal.audio, internal.total, false, style)).append('\n');
                known += internal.images + internal.videos + internal.audio;
                if (internal.otherFiles > 0) {
                    b.append(row("Other files", internal.otherFiles, internal.total, false, style)).append('\n');
                    known += internal.otherFiles;
                }
            }
            if (apps != null || media) {
                b.append(row("System & other", Math.max(0, internal.used() - known), internal.total, false, style)).append('\n');
            }
            b.append(row("Free", internal.free, internal.total, false, style)).append('\n');
        }

        if (others != null) {
            for (Volume v : others) {
                if (v.total <= 0) continue;
                b.append('\n').append(v.name).append(": ").append(size(v.used(), false)).append(" of ")
                        .append(size(v.total, false)).append(" used\n")
                        .append(StatusBars.bar(percent(v.used(), v.total), HEADER_BAR_WIDTH, style)).append('\n');
            }
        }

        if (apps == null) {
            b.append("\nTo see which apps use the most space,\ngive HelCLI Usage access: storage access");
        } else if (top > 0 && !apps.isEmpty()) {
            b.append('\n').append(biggestApps(apps, top, false, style));
        }
        return b.toString().replaceAll("\n+$", "");
    }

    /** The biggest apps, by total size or (byCache) by cache, which can be cleared safely. */
    public static String biggestApps(List<App> apps, int top, boolean byCache, String style) {
        List<App> sorted = new ArrayList<>(apps);
        Collections.sort(sorted, (x, y) -> Long.compare(byCache ? y.cache : y.total(), byCache ? x.cache : x.total()));

        long biggest = sorted.isEmpty() ? 1 : Math.max(1, byCache ? sorted.get(0).cache : sorted.get(0).total());
        StringBuilder b = new StringBuilder(byCache ? "Apps with the most cache (safe to clear):" : "Biggest apps:");
        int shown = 0;
        for (App a : sorted) {
            if (shown >= top) break;
            long v = byCache ? a.cache : a.total();
            if (v <= 0) break;
//            bars compare apps with the biggest one, so small apps are still visible
            b.append('\n').append(row(a.name, v, biggest, false, style));
            shown++;
        }
        if (shown == 0) b.append("\n  (none)");
        if (sorted.size() > shown && top < sorted.size()) {
            b.append('\n').append(byCache ? "Full list: storage cache all" : "Full list: storage apps all");
        }
        return b.toString();
    }
}
