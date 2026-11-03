package ohi.andre.consolelauncher.managers.at;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AtJobsTest {

    private static final TimeZone CHI = TimeZone.getTimeZone("America/Chicago");
    // Tue 2026-11-03 20:00 in Chicago
    private static final long NOW = 1793757600000L;
    private static final long MIN = 60_000L, H = 3_600_000L;

    private static AtJobs.Job ok(String input) {
        AtJobs.Parsed p = AtJobs.parse(input, 1, NOW, CHI);
        assertNull(p.error, p.error);
        return p.job;
    }

    private static String error(String input) {
        AtJobs.Parsed p = AtJobs.parse(input, 1, NOW, CHI);
        assertNull(p.job);
        assertNotNull(p.error);
        return p.error;
    }

    @Test
    public void clockTimesLaterToday() {
        AtJobs.Job j = ok("22:00 theme -apply oled");
        assertEquals(NOW + 2 * H, j.at);
        assertEquals("theme -apply oled", j.command);
        assertFalse(j.daily);

        assertEquals(NOW + 2 * H + 30 * MIN, ok("10:30pm flash").at);
        assertEquals(NOW + 2 * H + 30 * MIN, ok("10:30 PM flash").at);
        assertEquals(NOW + 3 * H, ok("11pm flash").at);
    }

    @Test
    public void timesThatPassedMeanTomorrow() {
        assertEquals(NOW + 11 * H, ok("7:00 cal").at);
        assertEquals(NOW + 24 * H, ok("20:00 cal").at);   // exactly now -> tomorrow
        assertEquals(NOW + 16 * H, ok("12pm cal").at);    // noon tomorrow
        assertEquals(NOW + 4 * H, ok("12am cal").at);     // midnight
    }

    @Test
    public void relativeTimes() {
        assertEquals(NOW + 30 * MIN, ok("+30m flash").at);
        assertEquals(NOW + 2 * H, ok("2h flash").at);
        assertEquals(NOW + 90 * MIN, ok("1h30m flash").at);
    }

    @Test
    public void dailyJobs() {
        AtJobs.Job j = ok("daily 7:30am cal today");
        assertTrue(j.daily);
        assertEquals(7, j.hour);
        assertEquals(30, j.minute);
        assertEquals("cal today", j.command);
//        after it runs, it moves to the next morning
        assertEquals(j.at + 24 * H, j.next(j.at, CHI).at);
    }

    @Test
    public void mistakes() {
        assertTrue(error("22:00").startsWith("What should run"));
        assertTrue(error("").startsWith("Say when and what"));
        assertTrue(error("25:00 flash").startsWith("That isn't a time"));
        assertTrue(error("13pm flash").startsWith("That isn't a time"));
        assertTrue(error("soon flash").startsWith("That isn't a time"));
        assertTrue(error("7 flash").startsWith("That isn't a time"));      // ambiguous: 7am, 7pm or 7 minutes?
        assertTrue(error("daily 30m flash").startsWith("Daily jobs"));
        assertTrue(error("0m flash").startsWith("Pick at least"));
    }

    @Test
    public void savesAndLoads() {
        List<AtJobs.Job> jobs = Arrays.asList(ok("22:00 theme -apply oled"), AtJobs.parse("daily 7am cal", 2, NOW, CHI).job);
        List<AtJobs.Job> back = AtJobs.fromJson(AtJobs.toJson(jobs));
        assertEquals(2, back.size());
        assertEquals("theme -apply oled", back.get(0).command);
        assertTrue(back.get(1).daily);
        assertEquals(3, AtJobs.nextId(back));
        assertTrue(AtJobs.fromJson("not json").isEmpty());
    }

    @Test
    public void listsJobs() {
        List<AtJobs.Job> jobs = Arrays.asList(ok("22:00 theme -apply oled"), AtJobs.parse("daily 7am cal", 2, NOW, CHI).job);
        assertEquals("1  22:00  theme -apply oled  (in 2h 0m)\n2  07:00 daily  cal  (in 11h 0m)\nCancel one with: at cancel [number]",
                AtJobs.list(jobs, NOW, CHI, true));
    }
}
