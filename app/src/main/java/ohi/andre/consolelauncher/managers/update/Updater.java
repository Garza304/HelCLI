package ohi.andre.consolelauncher.managers.update;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.managers.sysinfo.Breakdown;
import ohi.andre.consolelauncher.tuils.GenericFileProvider;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Updates HelCLI from its GitHub releases. Before installing, the download must match the size and
 * SHA-256 GitHub reports, and be signed with the same key as the installed app; Android's installer
 * then checks the signature again. Call everything here off the main thread.
 */
public class Updater {

    public interface Output {
        void line(String text);
    }

    private Updater() {}

    public static Release latest(OkHttpClient client) throws Exception {
        Request r = new Request.Builder().url(Release.LATEST_URL).cacheControl(CacheControl.FORCE_NETWORK)
                .header("Accept", "application/vnd.github+json").header("User-Agent", "HelCLI").get().build();
        try (Response response = client.newCall(r).execute()) {
            if (response.code() == 404) throw new Exception("there are no releases yet");
            if (!response.isSuccessful() || response.body() == null) throw new Exception("GitHub answered " + response.code());
            return Release.parse(response.body().string());
        }
    }

    /** "update": says whether there's a newer version. */
    public static void check(Context c, OkHttpClient client, Output out) {
        try {
            Release r = latest(client);
            String installed = BuildConfig.VERSION_NAME;
            if (!r.newerThan(installed)) {
                out.line("HelCLI " + installed + " is up to date.");
                return;
            }
            out.line("HelCLI " + r.version + " is available (you have " + installed + ")"
                    + (r.headline().length() > 0 ? "\n  " + r.headline() : "")
                    + "\n" + Breakdown.size(r.size, false) + " download. Install it with: update install");
        } catch (Exception e) {
            out.line("update: couldn't check for updates (" + e.getMessage() + ")");
        }
    }

    /** "update install": downloads, checks and opens Android's installer. */
    public static void install(Context c, OkHttpClient client, boolean evenIfCurrent, Output out) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !c.getPackageManager().canRequestPackageInstalls()) {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(i);
            } catch (Exception ignored) {
            }
            out.line("Allow HelCLI to install apps (just this once), then run: update install");
            return;
        }

        Release r;
        try {
            r = latest(client);
        } catch (Exception e) {
            out.line("update: couldn't check for updates (" + e.getMessage() + ")");
            return;
        }
        if (!evenIfCurrent && !r.newerThan(BuildConfig.VERSION_NAME)) {
            out.line("HelCLI " + BuildConfig.VERSION_NAME + " is up to date. To reinstall it anyway: update reinstall");
            return;
        }

        File dir = new File(c.getExternalFilesDir(null), "updates");
        if (!dir.exists()) dir.mkdirs();
        File apk = new File(dir, "helcli-" + r.version + ".apk");
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) if (!f.equals(apk)) f.delete();

        out.line("Downloading HelCLI " + r.version + " (" + Breakdown.size(r.size, false) + ")...");
        String sha;
        try {
            sha = download(client, r, apk, out);
        } catch (Exception e) {
            apk.delete();
            out.line("update: the download failed (" + e.getMessage() + ")");
            return;
        }

        if (r.size > 0 && apk.length() != r.size) {
            apk.delete();
            out.line("update: the download is incomplete. Try again.");
            return;
        }
        if (r.sha256 != null && !r.sha256.equals(sha)) {
            apk.delete();
            out.line("update: the download doesn't match GitHub's checksum, so it wasn't installed. Try again.");
            return;
        }
        if (!sameSigner(c, apk)) {
            apk.delete();
            out.line("update: this release is signed with a different key than your HelCLI, so Android won't install it over yours."
                    + "\nThat happens when HelCLI came from a build made before releases were signed. Install it once from "
                    + Release.RELEASES_PAGE + " (uninstall first; git pull brings back your my-config settings). After that, update works.");
            return;
        }

        Uri uri = FileProvider.getUriForFile(c, GenericFileProvider.PROVIDER_NAME, apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            out.line("Checked: size, checksum and signature match. Tap Update to install HelCLI " + r.version + ".");
        } catch (Exception e) {
            out.line("update: couldn't open the installer (" + e.getMessage() + ")");
        }
    }

    /** Saves the file and returns its SHA-256 as lowercase hex, reporting progress every quarter. */
    private static String download(OkHttpClient client, Release r, File to, Output out) throws Exception {
        Request req = new Request.Builder().url(r.downloadUrl).header("User-Agent", "HelCLI").get().build();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (Response response = client.newCall(req).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new Exception("GitHub answered " + response.code());
            long total = r.size > 0 ? r.size : response.body().contentLength();
            try (InputStream in = response.body().byteStream(); OutputStream os = new FileOutputStream(to)) {
                byte[] buffer = new byte[64 * 1024];
                long done = 0;
                int nextQuarter = 1, n;
                while ((n = in.read(buffer)) > 0) {
                    os.write(buffer, 0, n);
                    digest.update(buffer, 0, n);
                    done += n;
                    if (total > 0 && nextQuarter < 4 && done * 4 >= total * nextQuarter) {
                        out.line("  " + (nextQuarter * 25) + "%");
                        nextQuarter++;
                    }
                }
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b));
        return hex.toString();
    }

    /** Whether the APK is signed with the same key as the installed HelCLI. */
    @SuppressWarnings("deprecation")
    static boolean sameSigner(Context c, File apk) {
        try {
            PackageManager pm = c.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo installed = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                PackageInfo download = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (download == null || download.signingInfo == null || installed.signingInfo == null) return false;
                if (!c.getPackageName().equals(download.packageName)) return false;
                return Arrays.equals(installed.signingInfo.getApkContentsSigners(), download.signingInfo.getApkContentsSigners());
            }
            PackageInfo installed = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES);
            PackageInfo download = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
            if (download == null || !c.getPackageName().equals(download.packageName)) return false;
            Signature[] a = installed.signatures, b = download.signatures;
            return a != null && b != null && Arrays.equals(a, b);
        } catch (Exception e) {
            return false;
        }
    }
}
