package ohi.andre.consolelauncher.managers.sync;

import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RepoSyncTest {

    @Test
    public void readsShortRemotes() {
        RepoSync.Remote r = RepoSync.parseRemote("Garza304/HelCLI/my-config");
        assertEquals("Garza304", r.owner);
        assertEquals("HelCLI", r.repo);
        assertEquals("my-config", r.path);
        assertNull(r.branch);
        assertEquals("HEAD", r.ref());
        assertEquals("Garza304/HelCLI/my-config", r.toString());

        r = RepoSync.parseRemote("you/dotfiles/phone/helcli@dev");
        assertEquals("phone/helcli", r.path);
        assertEquals("dev", r.branch);
        assertEquals("you/dotfiles/phone/helcli@dev", r.toString());

        r = RepoSync.parseRemote("you/dotfiles");
        assertEquals("", r.path);
    }

    @Test
    public void readsGithubLinks() {
        RepoSync.Remote r = RepoSync.parseRemote("https://github.com/Garza304/HelCLI/tree/master/my-config");
        assertEquals("Garza304", r.owner);
        assertEquals("HelCLI", r.repo);
        assertEquals("master", r.branch);
        assertEquals("my-config", r.path);

        r = RepoSync.parseRemote("https://github.com/you/dotfiles.git");
        assertEquals("dotfiles", r.repo);
        assertNull(r.branch);
    }

    @Test
    public void rejectsBadRemotes() {
        assertNull(RepoSync.parseRemote(""));
        assertNull(RepoSync.parseRemote("justaname"));
        assertNull(RepoSync.parseRemote("you/repo/../../etc"));
        assertNull(RepoSync.parseRemote("https://gitlab.com/you/repo"));
        assertNull(RepoSync.parseRemote("you/repo@"));
        assertNull(RepoSync.parseRemote("you/repo@bad..branch"));
    }

    @Test
    public void buildsUrlsPinnedToTheCommit() {
        RepoSync.Remote r = RepoSync.parseRemote("Garza304/HelCLI/my-config@master");
        String sha = "8e6a913ef2de6df8e22709be7ee16ebefe7388b2";
        assertEquals("https://api.github.com/repos/Garza304/HelCLI/commits/master", r.commitUrl());
        assertEquals("https://api.github.com/repos/Garza304/HelCLI/contents/my-config/themes?ref=" + sha, r.contentsUrl("themes", sha));
        assertEquals("https://raw.githubusercontent.com/Garza304/HelCLI/" + sha + "/my-config/ui.xml", r.rawUrl("ui.xml", sha));
        assertEquals("https://api.github.com/repos/a/b/contents?ref=" + sha, RepoSync.parseRemote("a/b").contentsUrl("", sha));
    }

    @Test
    public void readsOnlyTheSettingsInTheFile() throws Exception {
        Map<String, String> e = RepoSync.readEntries("<?xml version=\"1.0\"?>\n<UI>\n"
                + "  <!-- <fullscreen value=\"true\"/> -->\n"
                + "  <input_bottom value=\"false\"/>\n"
                + "  <username value=\"garza\"/>\n</UI>");
        assertEquals(2, e.size());
        assertEquals("false", e.get("input_bottom"));
        assertEquals("garza", e.get("username"));
    }

    @Test
    public void plansOnlyRealChanges() {
        Map<String, String> current = new HashMap<>();
        current.put("input_bottom", "true");
        current.put("username", "garza");

        Map<String, String> incoming = new LinkedHashMap<>();
        incoming.put("input_bottom", "false");      // changed
        incoming.put("username", "garza");          // same
        incoming.put("fullscreen", "false");        // not on the phone yet, same as the default
        incoming.put("input_output_size", "18");    // not on the phone yet, differs from the default
        incoming.put("not_a_setting", "1");         // unknown
        incoming.put("show_ram", "maybe");          // wrong type

        RepoSync.Plan plan = RepoSync.plan(XMLPrefsManager.XMLPrefsRoot.UI, current, incoming);

        assertEquals(2, plan.changes.size());
        assertEquals("input_bottom", plan.changes.get(0).key);
        assertEquals("true", plan.changes.get(0).oldValue);
        assertEquals("false", plan.changes.get(0).newValue);
        assertEquals("input_output_size", plan.changes.get(1).key);
        assertEquals("15", plan.changes.get(1).oldValue);

        assertEquals(2, plan.warnings.size());
        assertTrue(plan.warnings.get(0).contains("not_a_setting"));
        assertTrue(plan.warnings.get(1).contains("show_ram"));
    }

    @Test
    public void checksColors() {
        Map<String, String> incoming = new LinkedHashMap<>();
        incoming.put("input_color", "#ffcc00");
        incoming.put("output_color", "white");

        RepoSync.Plan plan = RepoSync.plan(XMLPrefsManager.XMLPrefsRoot.THEME, new HashMap<String, String>(), incoming);
        assertEquals(1, plan.changes.size());
        assertEquals("input_color", plan.changes.get(0).key);
        assertEquals(1, plan.warnings.size());
    }

    @Test
    public void mapsFileNames() {
        assertEquals(XMLPrefsManager.XMLPrefsRoot.THEME, RepoSync.prefsRootFor("theme.xml"));
        assertEquals(XMLPrefsManager.XMLPrefsRoot.BEHAVIOR, RepoSync.prefsRootFor("behavior.xml"));
        assertNull(RepoSync.prefsRootFor("apps.xml"));
        assertNull(RepoSync.prefsRootFor("README.md"));
    }

    @Test
    public void theStarterFolderInTheRepoChangesNothing() throws Exception {
//        my-config/ ships with every example commented out, so a first pull is safe
        java.io.File dir = new java.io.File("../my-config");
        if (!dir.isDirectory()) dir = new java.io.File("my-config");
        assertTrue(dir.isDirectory());
        for (java.io.File f : dir.listFiles()) {
            XMLPrefsManager.XMLPrefsRoot root = RepoSync.prefsRootFor(f.getName());
            if (root == null) continue;
            String xml = new java.util.Scanner(f, "UTF-8").useDelimiter("\\A").next();
            assertEquals(f.getName(), 0, RepoSync.readEntries(xml).size());
        }
    }
}
