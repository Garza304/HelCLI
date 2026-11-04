package ohi.andre.consolelauncher.managers.ask;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ThinkingTest {

    @Test
    public void spinsAndCounts() {
        assertEquals("[|] Thinking... 0s", Thinking.frame(0, 0));
        assertEquals("[/] Thinking... 0s", Thinking.frame(200, 0));
        assertEquals("[-] Thinking... 0s", Thinking.frame(400, 0));
        assertEquals("[\\] Thinking... 0s", Thinking.frame(600, 0));
        assertEquals("[|] Thinking... 1s", Thinking.frame(1600, 0));
    }

    @Test
    public void changesWordEveryFewSeconds() {
        assertEquals("[|] Pondering... 2s", Thinking.frame(2600, 0).replaceAll("\\[.\\]", "[|]"));
        assertNotEquals(Thinking.frame(0, 3), Thinking.frame(0, 4));
//        wraps around the list
        assertEquals(Thinking.frame(0, 0).substring(4), Thinking.frame(Thinking.WORDS.length * Thinking.WORD_MS, 0).substring(4).replaceAll("\\d+m \\d+s|\\d+s", "0s"));
    }

    @Test
    public void saysHowLongItTook() {
        assertEquals("Claude · 6s", Thinking.done(6_400));
        assertEquals("Claude · 1m 05s", Thinking.done(65_000));
    }
}
