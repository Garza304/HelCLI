package ohi.andre.consolelauncher.managers.update;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The newest HelCLI release on GitHub, read from api.github.com/repos/Garza304/HelCLI/releases/latest.
 * Plain Java, so it can be unit tested.
 */
public class Release {

    public static final String LATEST_URL = "https://api.github.com/repos/Garza304/HelCLI/releases/latest";
    public static final String RELEASES_PAGE = "https://github.com/Garza304/HelCLI/releases";
    public static final String ASSET = "helcli.apk";

    public final String version;
    /** The build number: 1.0.42 is 42. */
    public final int build;
    public final String downloadUrl;
    public final long size;
    /** Lowercase hex SHA-256 of the APK as GitHub reports it, or null if GitHub didn't say. */
    public final String sha256;
    public final String notes;

    Release(String version, int build, String downloadUrl, long size, String sha256, String notes) {
        this.version = version;
        this.build = build;
        this.downloadUrl = downloadUrl;
        this.size = size;
        this.sha256 = sha256;
        this.notes = notes;
    }

    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)$");

    /** 42 for "1.0.42" or "v1.0.42", -1 if it isn't a HelCLI version. */
    public static int buildOf(String version) {
        if (version == null) return -1;
        Matcher m = VERSION.matcher(version.trim());
        return m.matches() ? Integer.parseInt(m.group(3)) : -1;
    }

    /** Reads GitHub's answer. Throws if it has no helcli.apk to download. */
    public static Release parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        String tag = o.getString("tag_name");
        String version = tag.startsWith("v") ? tag.substring(1) : tag;

        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (!ASSET.equals(a.optString("name"))) continue;

                String url = a.getString("browser_download_url");
                if (!url.startsWith("https://github.com/") && !url.startsWith("https://objects.githubusercontent.com/")) {
                    throw new JSONException("unexpected download address");
                }
                String digest = a.optString("digest", "");
                String sha = digest.toLowerCase(Locale.ROOT).startsWith("sha256:") ? digest.substring(7).toLowerCase(Locale.ROOT) : null;
                if (sha != null && !sha.matches("[0-9a-f]{64}")) sha = null;
                return new Release(version, buildOf(version), url, a.optLong("size", -1), sha, o.optString("body", "").trim());
            }
        }
        throw new JSONException("the release has no " + ASSET);
    }

    /** Whether this release is newer than the installed version. Local builds (1.0.0) always count as older. */
    public boolean newerThan(String installedVersion) {
        int installed = buildOf(installedVersion);
        return build > 0 && build > installed;
    }

    /** The first line of the notes: the change this release was built from. */
    public String headline() {
        if (notes.length() == 0) return "";
        String first = notes.split("\n")[0].trim();
        return first.length() > 80 ? first.substring(0, 79) + "…" : first;
    }
}
