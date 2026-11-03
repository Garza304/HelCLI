package ohi.andre.consolelauncher.managers.sysinfo;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BreakdownTest {

    private static final String STYLE = "[#-]";
    private static final long GB = 1000L * 1000 * 1000, GIB = 1024L * 1024 * 1024, MB = 1000L * 1000;

//    from a real phone with 8 GB
    private static final String MEMINFO = "MemTotal:        7812345 kB\n"
            + "MemFree:          312000 kB\n"
            + "MemAvailable:    2621440 kB\n"
            + "Buffers:            8000 kB\n"
            + "Cached:          2200000 kB\n"
            + "SwapTotal:       4194300 kB\n"
            + "SwapFree:        2936000 kB\n"
            + "Unparseable line\n";

    @Test
    public void formatsSizes() {
        assertEquals("7.45 GB", Breakdown.size(7812345L * 1024, true));
        assertEquals("128 GB", Breakdown.size(128 * GB, false));
        assertEquals("12.3 GB", Breakdown.size(12300 * MB, false));
        assertEquals("850 MB", Breakdown.size(850 * MB, false));
        assertEquals("0 B", Breakdown.size(-5, false));
    }

    @Test
    public void readsMeminfo() {
        Map<String, Long> m = Breakdown.parseMeminfo(MEMINFO);
        assertEquals(7812345L * 1024, (long) m.get("MemTotal"));
        assertEquals(2621440L * 1024, (long) m.get("MemAvailable"));
        assertFalse(m.containsKey("Unparseable line"));
    }

    @Test
    public void ramAddsUp() {
        Breakdown.Ram r = new Breakdown.Ram().fromMeminfo(Breakdown.parseMeminfo(MEMINFO));
        r.threshold = 216L * 1024 * 1024;
        r.self = 85L * 1024 * 1024;
        String out = Breakdown.ram(r, STYLE);

//        in use + cached + free = total
        assertEquals(r.total, (r.total - r.available) + (r.available - r.free) + r.free);
        assertTrue(out, out.startsWith("RAM: 4.95 GB of 7.45 GB in use (66%)"));
        assertTrue(out.contains("  In use          4.95 GB  [#######---]"));
        assertTrue(out.contains("  Cached          2.20 GB  [###-------]\n"));
        assertTrue(out.contains("  Free             305 MB  [#---------]"));
        assertTrue(out.contains("  Swap (zram)     1.20 GB  [###-------]"));
        assertTrue(out.contains("Cached memory is freed when apps need it."));
//        every line fits a phone's terminal
        for (String line : out.split("\n")) assertTrue(line, line.length() <= 46 || line.startsWith("Android starts"));
        assertTrue(out.contains("Android starts closing background apps below 216 MB available. 2.50 GB is available now."));
        assertTrue(out.contains("HelCLI itself is using 85.0 MB."));
    }

    @Test
    public void ramWithoutMeminfoStillWorks() {
//        only what ActivityManager gives, when /proc/meminfo can't be read
        Breakdown.Ram r = new Breakdown.Ram();
        r.total = 8 * GIB;
        r.available = 3 * GIB;
        r.low = true;
        String out = Breakdown.ram(r, STYLE);
        assertTrue(out.contains("Available"));
        assertFalse(out.contains("Cached"));
        assertTrue(out.contains("Memory is low"));
        assertEquals("Couldn't read the memory information", Breakdown.ram(new Breakdown.Ram(), STYLE));
    }

    private static List<Breakdown.App> apps() {
        return new ArrayList<>(Arrays.asList(
                new Breakdown.App("Spotify", 200 * MB, 9800 * MB, 3000 * MB),
                new Breakdown.App("Chrome", 400 * MB, 1600 * MB, 900 * MB),
                new Breakdown.App("Camera", 50 * MB, 50 * MB, 0),
                new Breakdown.App("A game with a very long name", 4000 * MB, 1000 * MB, 100 * MB)));
    }

    @Test
    public void storageWithEverything() {
        Breakdown.Volume v = new Breakdown.Volume("Internal", 128 * GB, 30 * GB);
        v.images = 12 * GB;
        v.videos = 20 * GB;
        v.audio = 1 * GB;
        v.otherFiles = 3 * GB;
        Breakdown.Volume sd = new Breakdown.Volume("SD card", 64 * GB, 60 * GB);

        String out = Breakdown.storage(v, Arrays.asList(sd), apps(), 3, STYLE);
        assertTrue(out, out.startsWith("Storage: 98.0 GB of 128 GB used (77%)"));
        assertTrue(out.contains("  Apps            17.1 GB  [#---------]\n"));
        assertTrue(out.contains("  Videos          20.0 GB  [##--------]"));
//        98 used - 17.1 apps - 36 media and files
        assertTrue(out.contains("  System & other  44.9 GB"));
        assertTrue(out.contains("SD card: 4.00 GB of 64.0 GB used"));
        assertTrue(out.contains("Biggest apps:\n  Spotify         10.0 GB  [##########]"));
        assertTrue(out.contains("  A game with a…  5.00 GB  [#####-----]"));
        assertTrue(out.contains("Full list: storage -apps all"));
        assertFalse(out.contains("Camera"));
        for (String line : out.split("\n")) assertTrue(line, line.length() <= 46);
    }

    @Test
    public void storageWithoutUsageAccess() {
        String out = Breakdown.storage(new Breakdown.Volume("Internal", 64 * GB, 10 * GB), null, null, 5, STYLE);
        assertTrue(out.startsWith("Storage: 54.0 GB of 64.0 GB used (84%)"));
        assertTrue(out.contains("  Free            10.0 GB"));
        assertFalse(out.contains("System & other"));
        assertTrue(out.endsWith("give HelCLI Usage access: storage -access"));
    }

    @Test
    public void biggestCaches() {
        String out = Breakdown.biggestApps(apps(), 10, true, STYLE);
        assertTrue(out.startsWith("Apps with the most cache (safe to clear):\n  Spotify         3.00 GB"));
//        apps without cache aren't listed
        assertFalse(out.contains("Camera"));
        assertFalse(out.contains("Full list"));
    }
}
