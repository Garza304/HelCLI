package ohi.andre.consolelauncher.managers.claude;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ohi.andre.consolelauncher.managers.ask.AskKey;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * "usage -link" and "ask -link": the closest HelCLI can get to linking an account. Anthropic has no
 * sign-in for other apps, so HelCLI opens the Claude Console page where the key is made, and when
 * you come back it picks the key you copied up from the clipboard, saves it and clears the clipboard.
 */
public class KeyLink {

    private KeyLink() {}

    public enum Kind {
        /** The usage line's API spend: an Admin API key, or a Claude Enterprise read:analytics key. */
        ADMIN("https://platform.claude.com/settings/admin-keys", "Admin key"),
        /** ask: an ordinary Claude API key. */
        API("https://platform.claude.com/settings/keys", "API key");

        public final String url, name;

        Kind(String url, String name) {
            this.url = url;
            this.name = name;
        }
    }

    /** How long HelCLI keeps waiting for the key after the page was opened. */
    static final long WAIT_MS = 30 * 60_000L;

    private static final String PREFS = "helcli_link", PENDING = "pending", SINCE = "since", REMINDED = "reminded";

    /** What's on the clipboard, compared with the key HelCLI is waiting for. */
    public enum Found { KEY, OTHER_KIND, NOTHING }

    /** Plain Java, so it can be unit tested. */
    public static Found classify(Kind waitingFor, String clip) {
        String c = ClipboardKey.find(clip);
        if (c == null) return Found.NOTHING;
        boolean admin = ClaudeUsage.looksLikeAdminKey(c), api = AskKey.looksLikeApiKey(c);
        if (waitingFor == Kind.ADMIN && admin) return Found.KEY;
//        a Claude Enterprise analytics key (sk-ant-api01-) also looks like an API key: usage takes it
        if (waitingFor == Kind.API && api && !c.startsWith("sk-ant-admin")) return Found.KEY;
        if (admin || api) return Found.OTHER_KIND;
        return Found.NOTHING;
    }

    /** Opens the page where the key is made, and starts waiting for it. */
    public static String start(Context context, Kind kind) {
        prefs(context).edit().putString(PENDING, kind.name()).putLong(SINCE, System.currentTimeMillis()).putBoolean(REMINDED, false).apply();

        boolean opened;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(kind.url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            opened = true;
        } catch (Exception e) {
            opened = false;
        }

        String where = opened ? "Opening " + kind.url : "Open " + kind.url + " in your browser";
        if (kind == Kind.ADMIN) {
            return where + Tuils.NEWLINE
                    + "1. Click Create admin key, name it HelCLI and pick a short expiration (it can manage your whole organization)." + Tuils.NEWLINE
                    + "2. Tap Copy, then come back to HelCLI. It picks the key up from the clipboard." + Tuils.NEWLINE
                    + "Needs the admin role in a Claude Console organization. Personal account? Set one up first at"
                    + " platform.claude.com/settings/organization, or skip API spend: for your Pro/Max limits use usage -link plan." + Tuils.NEWLINE
                    + "Claude Enterprise: create a key with only the read:analytics scope in claude.ai > Organization settings > API,"
                    + " copy it, and come back.";
        }
        return where + Tuils.NEWLINE
                + "1. Click Create key, name it HelCLI, and pick one workspace for it (Default is fine), so HelCLI doesn't need a workspace ID." + Tuils.NEWLINE
                + "2. Tap Copy, then come back to HelCLI. It picks the key up from the clipboard." + Tuils.NEWLINE
                + "Questions are billed to that Claude Console account; a Claude Pro or Max plan doesn't cover the API.";
    }

    /** Call when HelCLI is back in front: saves the key if one was copied. */
    public static void onBack(Context context) {
        SharedPreferences p = prefs(context);
        String pending = p.getString(PENDING, null);
        if (pending == null) return;
        if (System.currentTimeMillis() - p.getLong(SINCE, 0) > WAIT_MS) {
            p.edit().clear().apply();
            return;
        }

        Kind kind;
        try {
            kind = Kind.valueOf(pending);
        } catch (IllegalArgumentException e) {
            p.edit().clear().apply();
            return;
        }

        String clip = ClipboardKey.find(ClipboardKey.read(context));

        Found found = classify(kind, clip);
        if (found == Found.KEY) {
            p.edit().clear().apply();
            if (kind == Kind.ADMIN) {
                ClaudeUsageUpdater.saveAdminKey(context, clip);
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(new Intent(ClaudeUsageUpdater.ACTION_UPDATE));
            } else {
                AskKey.save(context, clip);
            }
            boolean cleared = ClipboardKey.clear(context);
            Tuils.sendOutput(context, "Linked: saved the " + kind.name + " " + ClaudeUsage.maskKey(clip)
                    + " in HelCLI's private storage." + (cleared ? " The clipboard was cleared." : " Clear your clipboard now.")
                    + Tuils.NEWLINE + (kind == Kind.ADMIN ? "The usage line updates now." : "Try: ask what can you do?"));
            return;
        }

//        say it once per link, not every time HelCLI comes back
        if (p.getBoolean(REMINDED, false)) return;
        p.edit().putBoolean(REMINDED, true).apply();
        if (found == Found.OTHER_KIND) {
            Tuils.sendOutput(context, kind == Kind.ADMIN
                    ? "That's an ordinary API key, not an Admin key, so it can't read costs. (It works for ask: ask -link.) Copy the Admin key and come back."
                    : "That's an Admin key, which can't ask Claude questions. Copy an ordinary API key and come back.");
        } else {
            Tuils.sendOutput(context, "Waiting for the " + kind.name + ": tap Copy on the key page, then come back to HelCLI."
                    + " (" + (kind == Kind.ADMIN ? "usage" : "ask") + " -link opens the page again.)");
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
