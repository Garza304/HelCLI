package ohi.andre.consolelauncher.managers.sysinfo;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QrTextTest {

    @Test
    public void drawsTwoRowsPerLine() {
//        a 3x3 checkerboard: dark corners and center
        List<String> lines = QrText.render(3, (x, y) -> (x + y) % 2 == 0);
//        3 + 2*2 margin = 7 wide, 7 rows -> 4 lines
        assertEquals(4, lines.size());
        assertEquals("       ", lines.get(0));          // margin rows
        assertEquals("  ▀▄▀  ", lines.get(1));          // rows 2,3 = code rows 0,1
        assertEquals("  ▀ ▀  ", lines.get(2));          // rows 4,5 = code row 2, then margin
        assertEquals("       ", lines.get(3));
    }

    @Test
    public void realCodesFitTheTerminal() {
        List<String> small = QrText.render("https://github.com/Garza304/HelCLI");
        assertTrue(small != null);
        for (String l : small) assertTrue(l.length() <= 41);
//        finder pattern: the top-left corner is a solid run
        assertTrue(small.get(1).startsWith("  █▀▀▀▀▀█"));

        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 300; i++) longText.append('x');
        assertNull(QrText.render(longText.toString()));
    }
}
