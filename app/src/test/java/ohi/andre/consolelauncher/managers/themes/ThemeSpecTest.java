package ohi.andre.consolelauncher.managers.themes;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import ohi.andre.consolelauncher.managers.xml.options.Suggestions;
import ohi.andre.consolelauncher.managers.xml.options.Theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ThemeSpecTest {

    private static File galleryFolder() {
//        unit tests run from the app module, the gallery is at the repository root
        File f = new File("../themes");
        if (!f.isDirectory()) f = new File("themes");
        assertTrue("theme gallery folder not found", f.isDirectory());
        return f;
    }

    private static String read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f); Scanner s = new Scanner(in, StandardCharsets.UTF_8.name())) {
            return s.useDelimiter("\\A").hasNext() ? s.next() : "";
        }
    }

    @Test
    public void everyGalleryThemeIsValid() throws Exception {
        File[] files = galleryFolder().listFiles((dir, name) -> name.endsWith(".json") && !name.equals("index.json"));
        assertNotNull(files);
        assertTrue("expected the starter themes", files.length >= 20);

        for (File f : files) {
            String json = read(f);
            List<String> problems = ThemeSpec.validate(json);
            assertTrue(f.getName() + ": " + problems, problems.isEmpty());

            ThemeSpec spec = ThemeSpec.parse(json);
            assertEquals(f.getName(), spec.name + ".json");
            assertFalse(spec.theme.isEmpty());
        }
    }

    @Test
    public void thePresetsFromTheOldVersionStillExist() {
        for (String preset : new String[] {"blue", "red", "green", "pink", "bw", "cyberpunk"}) {
            assertTrue(preset, new File(galleryFolder(), preset + ".json").exists());
        }
    }

    @Test
    public void parsesAMinimalTheme() throws Exception {
        ThemeSpec spec = ThemeSpec.parse("{\"name\":\"Tokyo Night\",\"theme\":{\"input_color\":\"#7AA2F7\"}}");
        assertEquals("tokyo-night", spec.name);
        assertEquals("#7aa2f7", spec.themeColor(Theme.input_color));
//        colors the theme leaves out fall back to the defaults
        assertEquals(Theme.output_color.defaultValue(), spec.themeColor(Theme.output_color));
        assertEquals(Suggestions.apps_bg_color.defaultValue(), spec.suggestionColor(Suggestions.apps_bg_color));
    }

    @Test
    public void ignoresUnknownColorsButReportsThem() throws Exception {
        String json = "{\"name\":\"x\",\"theme\":{\"input_color\":\"#ffffff\",\"from_the_future\":\"#000000\"}}";
        ThemeSpec spec = ThemeSpec.parse(json);
        assertFalse(spec.theme.containsKey("from_the_future"));
        assertEquals(1, ThemeSpec.validate(json).size());
    }

    @Test
    public void rejectsBadThemes() {
        expectInvalid("not json");
        expectInvalid("{\"theme\":{\"input_color\":\"#ffffff\"}}");                       // no name
        expectInvalid("{\"name\":\"../evil\",\"theme\":{\"input_color\":\"#ffffff\"}}"); // unsafe name
        expectInvalid("{\"name\":\"x\",\"theme\":{\"input_color\":\"green\"}}");          // not a hex color
        expectInvalid("{\"name\":\"x\",\"theme\":{}}");                                  // no colors
    }

    private static void expectInvalid(String json) {
        try {
            ThemeSpec.parse(json);
            fail("should have been rejected: " + json);
        } catch (ThemeSpec.InvalidThemeException expected) {
        }
    }

    @Test
    public void acceptsColorLists() {
        assertTrue(ThemeSpec.isValidColor("#ff000000,#00000000, #123456"));
        assertFalse(ThemeSpec.isValidColor("#ff000000,"));
        assertFalse(ThemeSpec.isValidColor(""));
        assertFalse(ThemeSpec.isValidColor("#12345"));
    }

    @Test
    public void survivesARoundTrip() throws Exception {
        ThemeSpec spec = ThemeSpec.parse(read(new File(galleryFolder(), "dracula.json")));
        ThemeSpec again = ThemeSpec.parse(spec.toJson().toString());
        assertEquals(spec.name, again.name);
        assertEquals(spec.title, again.title);
        assertEquals(spec.theme, again.theme);
        assertEquals(spec.suggestions, again.suggestions);
    }
}
