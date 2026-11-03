package ohi.andre.consolelauncher.tuils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StatusBarsTest {

    @Test
    public void drawsBars() {
        assertEquals("[█████░░░░░]", StatusBars.bar(50, 12, "[█░]"));
        assertEquals("[##--]", StatusBars.bar(50, 6, "[#-]"));
        assertEquals("████░░░░", StatusBars.bar(50, 8, "█░"));
        assertEquals("[░░░░]", StatusBars.bar(-1, 6, "[█░]"));       // unknown value
        assertEquals("[█░░░]", StatusBars.bar(1, 6, "[█░]"));        // anything above zero shows
        assertEquals("[████]", StatusBars.bar(250, 6, "[█░]"));      // clamped
        assertEquals(StatusBars.bar(50, 4, StatusBars.DEFAULT_STYLE), StatusBars.bar(50, 4, "x"));  // bad style falls back
    }

    @Test
    public void fixedWidthBars() {
        String text = StatusBars.replaceCode("RAM 42% %bar:10", "bar", 42);
        assertTrue(StatusBars.hasMarkers(text));
        assertEquals("RAM 42% [███░░░░░]", StatusBars.render(text, 40, "[█░]"));
    }

    @Test
    public void fillingBarsUseTheRestOfTheLine() {
        String text = StatusBars.replaceCode("RAM %bar", "bar", 50);
        String line = StatusBars.render(text, 24, "[█░]");
        assertEquals(24, line.length());
        assertEquals("RAM [█████████░░░░░░░░░]", line);
    }

    @Test
    public void barsOnTheSameLineShareIt() {
        String text = StatusBars.replaceCode("A %bar", "bar", 100) + "  " + StatusBars.replaceCode("B %bar", "bar", 0);
        String line = StatusBars.render(text, 30, "[#-]");
//        "A " + "  " + "B " = 6 visible characters, 24 left for two bars
        assertEquals("A [##########]  B [----------]", line);
    }

    @Test
    public void eachLineIsMeasuredOnItsOwn() {
        String text = StatusBars.replaceCode("first line\nRAM %bar", "bar", 0);
        assertEquals("first line\nRAM [----]", StatusBars.render(text, 10, "[#-]"));
    }

    @Test
    public void narrowLinesStillGetAMinimumBar() {
        String text = StatusBars.replaceCode("a long label that fills the line %bar", "bar", 50);
        assertTrue(StatusBars.render(text, 20, "[#-]").endsWith("[##--]"));
    }

    @Test
    public void unknownWidthUsesAFallback() {
        String text = StatusBars.replaceCode("%bar", "bar", 0);
        assertEquals(StatusBars.FALLBACK_WIDTH, StatusBars.render(text, 0, "[#-]").length());
    }

    @Test
    public void longerCodesAreReplacedFirst() {
//        storage replaces %ebar (external) before %bar (internal)
        String text = StatusBars.replaceCode("%ebar:6 %bar:6", "ebar", 100);
        text = StatusBars.replaceCode(text, "bar", 0);
        assertEquals("[####] [----]", StatusBars.render(text, 40, "[#-]"));
        assertFalse(StatusBars.hasMarkers(StatusBars.replaceCode("no codes here", "bar", 5)));
    }

    @Test
    public void addsAGapBetweenItems() {
        assertEquals("  ", StatusBars.gapBetween("WiFi - Home", "Claude", "  "));
        assertEquals("", StatusBars.gapBetween("", "Claude", "  "));                 // first on the line
        assertEquals("", StatusBars.gapBetween("Unlocked 3 times\n", "Claude", "  ")); // previous ends the line
        assertEquals("", StatusBars.gapBetween("WiFi", "Claude", ""));              // turned off
    }
}
