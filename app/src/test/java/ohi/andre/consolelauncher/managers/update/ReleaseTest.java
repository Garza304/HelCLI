package ohi.andre.consolelauncher.managers.update;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReleaseTest {

//    the shape of api.github.com/repos/{owner}/{repo}/releases/latest
    private static final String JSON = "{\"tag_name\":\"v1.0.42\",\"name\":\"HelCLI 1.0.42\","
            + "\"body\":\"Add the update command\\n\\nBuilt from abc1234. Install it, or run \\\"update\\\" in HelCLI.\","
            + "\"assets\":[{\"name\":\"notes.txt\",\"browser_download_url\":\"https://github.com/x/notes.txt\"},"
            + "{\"name\":\"helcli.apk\",\"size\":24532809,"
            + "\"digest\":\"sha256:1DA8C60177609C9C11401E73EE444C18DB0CEC21F8DE6D998BC3CC4CB3D22020\","
            + "\"browser_download_url\":\"https://github.com/Garza304/HelCLI/releases/download/v1.0.42/helcli.apk\"}]}";

    @Test
    public void readsTheRelease() throws Exception {
        Release r = Release.parse(JSON);
        assertEquals("1.0.42", r.version);
        assertEquals(42, r.build);
        assertEquals(24532809L, r.size);
        assertEquals("1da8c60177609c9c11401e73ee444c18db0cec21f8de6d998bc3cc4cb3d22020", r.sha256);
        assertEquals("https://github.com/Garza304/HelCLI/releases/download/v1.0.42/helcli.apk", r.downloadUrl);
        assertEquals("Add the update command", r.headline());
    }

    @Test
    public void comparesVersions() throws Exception {
        Release r = Release.parse(JSON);
        assertTrue(r.newerThan("1.0.41"));
        assertFalse(r.newerThan("1.0.42"));
        assertFalse(r.newerThan("1.0.50"));
        assertTrue(r.newerThan("1.0.0"));           // built on a computer
        assertTrue(r.newerThan("v6.15.1-updated")); // not a HelCLI version
        assertEquals(-1, Release.buildOf("nonsense"));
    }

    @Test
    public void releasesWithoutTheApkAreRejected() {
        try {
            Release.parse("{\"tag_name\":\"v1.0.3\",\"assets\":[]}");
            fail();
        } catch (JSONException expected) {
        }
    }

    @Test
    public void downloadsOnlyFromGitHub() {
        try {
            Release.parse(JSON.replace("https://github.com/Garza304/HelCLI/releases/download/v1.0.42/helcli.apk", "https://evil.example.com/helcli.apk"));
            fail();
        } catch (JSONException expected) {
        }
    }

    @Test
    public void missingDigestIsAllowed() throws Exception {
        assertNull(Release.parse(JSON.replace("\"digest\":\"sha256:1DA8C60177609C9C11401E73EE444C18DB0CEC21F8DE6D998BC3CC4CB3D22020\",", "")).sha256);
    }
}
