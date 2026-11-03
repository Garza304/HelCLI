package ohi.andre.consolelauncher.managers.sync;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import ohi.andre.consolelauncher.managers.themes.ThemeSpec;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;

/**
 * The parts of "git pull" that don't need Android: reading a remote, reading config files and
 * working out which settings a pull changes.
 *
 * A config file in the repo only needs the settings you want to control. For example a ui.xml
 * containing just {@code <UI><input_bottom value="true"/></UI>} changes that one setting and leaves
 * every other setting on the phone alone. Full config files copied from the phone work too.
 */
public class RepoSync {

    private RepoSync() {}

//    ---------------------------------------------------------------- remotes

    /** Where to pull from: a folder in a GitHub repository, on a branch (null = the default branch). */
    public static class Remote {
        public final String owner, repo, path, branch;

        public Remote(String owner, String repo, String path, String branch) {
            this.owner = owner;
            this.repo = repo;
            this.path = path == null ? "" : path;
            this.branch = branch;
        }

        /** The short form, as typed in "git remote": owner/repo/path@branch */
        @Override
        public String toString() {
            return owner + "/" + repo + (path.length() > 0 ? "/" + path : "") + (branch != null ? "@" + branch : "");
        }

        public String ref() {
            return branch != null ? branch : "HEAD";
        }

        public String commitUrl() {
            return "https://api.github.com/repos/" + owner + "/" + repo + "/commits/" + ref();
        }

        public String contentsUrl(String subPath, String sha) {
            String p = join(path, subPath);
            return "https://api.github.com/repos/" + owner + "/" + repo + "/contents" + (p.length() > 0 ? "/" + p : "") + "?ref=" + sha;
        }

        /** Raw file at an exact commit: never served stale from a cache. */
        public String rawUrl(String subPath, String sha) {
            return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + sha + "/" + join(path, subPath);
        }

        public String webUrl() {
            return "https://github.com/" + owner + "/" + repo + (path.length() > 0 || branch != null
                    ? "/tree/" + (branch != null ? branch : "HEAD") + (path.length() > 0 ? "/" + path : "") : "");
        }
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern WEB_URL = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/tree/([^/]+)(/.*)?)?/?$");

    /**
     * Reads a remote. Accepted forms:
     * owner/repo, owner/repo/folder, owner/repo/folder@branch, owner/repo@branch,
     * https://github.com/owner/repo and https://github.com/owner/repo/tree/branch/folder
     * Returns null when the text isn't one of these.
     */
    public static Remote parseRemote(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() == 0) return null;

        Matcher web = WEB_URL.matcher(s);
        if (web.matches()) {
            String owner = web.group(1), repo = web.group(2), branch = web.group(3), path = clean(web.group(4));
            if (!NAME.matcher(owner).matches() || !NAME.matcher(repo).matches() || path == null) return null;
            if (branch != null && !validBranch(branch)) return null;
            return new Remote(owner, repo, path, branch);
        }
        if (s.contains("://")) return null;

        String branch = null;
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            branch = s.substring(at + 1).trim();
            s = s.substring(0, at).trim();
            if (!validBranch(branch)) return null;
        }

        String[] parts = s.split("/", 3);
        if (parts.length < 2) return null;
        if (!NAME.matcher(parts[0]).matches() || !NAME.matcher(parts[1]).matches()) return null;
        String path = clean(parts.length == 3 ? parts[2] : "");
        if (path == null) return null;

        return new Remote(parts[0], parts[1], path, branch);
    }

    private static boolean validBranch(String b) {
        return b.length() > 0 && b.matches("[A-Za-z0-9._/-]+") && !b.contains("..");
    }

    /** Normalizes a folder path, or returns null if it tries to escape the repository. */
    private static String clean(String path) {
        if (path == null) return "";
        StringBuilder out = new StringBuilder();
        for (String part : path.split("/")) {
            if (part.length() == 0 || part.equals(".")) continue;
            if (part.equals("..") || !NAME.matcher(part).matches()) return null;
            if (out.length() > 0) out.append('/');
            out.append(part);
        }
        return out.toString();
    }

    private static String join(String a, String b) {
        if (a == null || a.length() == 0) return b == null ? "" : b;
        if (b == null || b.length() == 0) return a;
        return a + "/" + b;
    }

//    ---------------------------------------------------------------- config files

    /** The settings file a repo file maps to (theme.xml, ui.xml, ...), or null if it isn't one. */
    public static XMLPrefsManager.XMLPrefsRoot prefsRootFor(String fileName) {
        for (XMLPrefsManager.XMLPrefsRoot r : XMLPrefsManager.XMLPrefsRoot.values()) {
            if (r.path.equalsIgnoreCase(fileName)) return r;
        }
        return null;
    }

    /** Reads every {@code <name value="..."/>} entry of a config file. Throws if the XML is broken. */
    public static Map<String, String> readEntries(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        Document d = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        Map<String, String> out = new LinkedHashMap<>();
        Element root = d.getDocumentElement();
        if (root == null) return out;

        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) n;
            if (e.hasAttribute(XMLPrefsManager.VALUE_ATTRIBUTE)) out.put(e.getTagName(), e.getAttribute(XMLPrefsManager.VALUE_ATTRIBUTE));
        }
        return out;
    }

    /** One setting a pull changes. */
    public static class Change {
        public final String file, key, oldValue, newValue;

        public Change(String file, String key, String oldValue, String newValue) {
            this.file = file;
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        @Override
        public String toString() {
            return file + " " + key + ": " + oldValue + " -> " + newValue;
        }
    }

    /** What pulling one config file would do. */
    public static class Plan {
        public final List<Change> changes = new ArrayList<>();
        /** Entries that were skipped, with the reason. */
        public final List<String> warnings = new ArrayList<>();
    }

    /**
     * Compares the repo's version of a config file with the phone's. Unknown settings and values
     * of the wrong type are skipped (and listed in warnings), so a typo can't break the launcher.
     *
     * @param current the phone's values (missing settings count as their defaults)
     */
    public static Plan plan(XMLPrefsManager.XMLPrefsRoot root, Map<String, String> current, Map<String, String> incoming) {
        Plan plan = new Plan();

        for (Map.Entry<String, String> e : incoming.entrySet()) {
            XMLPrefsSave option = find(root, e.getKey());
            if (option == null) {
                plan.warnings.add(root.path + ": " + e.getKey() + " isn't a setting, skipped");
                continue;
            }

            String value = e.getValue();
            String problem = checkValue(option, value);
            if (problem != null) {
                plan.warnings.add(root.path + ": " + e.getKey() + " " + problem + ", skipped");
                continue;
            }

            String old = current.containsKey(e.getKey()) ? current.get(e.getKey()) : option.defaultValue();
            if (old == null) old = "";
            if (!old.equals(value)) plan.changes.add(new Change(root.path, e.getKey(), old, value));
        }
        return plan;
    }

    private static XMLPrefsSave find(XMLPrefsManager.XMLPrefsRoot root, String key) {
        for (XMLPrefsSave s : root.enums) {
            if (s.label().equals(key)) return s;
        }
        return null;
    }

    /** Null if the value suits the setting, or a short description of the problem. */
    static String checkValue(XMLPrefsSave option, String value) {
        String type = option.type();
        if (XMLPrefsSave.COLOR.equals(type)) {
            if (value.length() == 0 && (option.defaultValue() == null || option.defaultValue().length() == 0)) return null;
            return ThemeSpec.isValidColor(value) ? null : "needs a color like #RRGGBB or #AARRGGBB (got \"" + value + "\")";
        }
        if (XMLPrefsSave.INTEGER.equals(type)) {
//            line order settings (ram_index, ...) can be decimals: 1.1 sits after 1.0 on the same line
            if (option.label().endsWith("_index")) {
                try {
                    Float.parseFloat(value.trim());
                    return null;
                } catch (NumberFormatException e) {
                    return "needs a number (got \"" + value + "\")";
                }
            }
            try {
                Integer.parseInt(value.trim());
                return null;
            } catch (NumberFormatException e) {
                return "needs a whole number (got \"" + value + "\")";
            }
        }
        if (XMLPrefsSave.BOOLEAN.equals(type)) {
            String v = value.trim().toLowerCase(Locale.ROOT);
            return v.equals("true") || v.equals("false") ? null : "needs true or false (got \"" + value + "\")";
        }
        return null;
    }

    /** Short commit id, like git prints. */
    public static String shortSha(String sha) {
        return sha == null ? "?" : sha.length() > 7 ? sha.substring(0, 7) : sha;
    }
}
