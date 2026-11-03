package ohi.andre.consolelauncher.commands.main.raw;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.main.specific.SimpleParamCommand;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * ask [question]: an answer from Claude, in the terminal. Needs a Claude API key (ask -key);
 * each question costs a little on that account.
 */
public class ask extends SimpleParamCommand {

    private static final String PREFS = "helcli_ask", KEY = "api_key", WARNED = "warned";

    public ask() {
        option("key", (pack, t) -> saveKey(pack.context));
        option("forget", (pack, t) -> {
            prefs(pack.context).edit().remove(KEY).apply();
            return "Deleted the Claude API key from HelCLI";
        });
        option("new", (pack, t) -> {
            if (Build.VERSION.SDK_INT >= 26) ohi.andre.consolelauncher.managers.ask.AskClaude.forgetConversation();
            return "Started a new conversation";
        });
    }

//    the question is free text, so only "-words" are options ("ask new ideas?" is a question)
    @Override
    public boolean paramsNeedDash() {
        return true;
    }

    @Override
    public boolean takesText() {
        return true;
    }

    @Override
    public String onText(ExecutePack pack, String input) {
        final Context c = pack.context;

//        the SDK's JSON library needs Android 8
        if (Build.VERSION.SDK_INT < 26) return "ask needs Android 8 or later";

        final String key = prefs(c).getString(KEY, null);
        if (key == null) {
            return "ask needs a Claude API key. Create one at platform.claude.com/settings/keys, copy it, then run: ask -key";
        }

        final String model = XMLPrefsManager.get(Behavior.ask_model).trim();
        final String effort = XMLPrefsManager.get(Behavior.ask_effort);
        final String question = input;

        String note = "";
        if (!prefs(c).getBoolean(WARNED, false)) {
            prefs(c).edit().putBoolean(WARNED, true).apply();
            note = " (each question costs a little on your Claude API account)";
        }

        new Thread(() -> Tuils.sendOutput(c, ohi.andre.consolelauncher.managers.ask.AskClaude.ask(key, model, effort, question))).start();
        return "Asking Claude..." + note;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Reads the key from the clipboard, so it never appears on screen or in the command history. */
    private static String saveKey(Context c) {
        ClipboardManager clipboard = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        String key = clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null
                ? clip.getItemAt(0).getText().toString().trim() : null;

        if (key == null || !key.matches("sk-ant-api\\d+-[A-Za-z0-9_-]{20,}")) {
            return "Copy your Claude API key (sk-ant-api03-...) first, then run ask -key again. Create one at platform.claude.com/settings/keys";
        }
        prefs(c).edit().putString(KEY, key).apply();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                clipboard.clearPrimaryClip();
            } catch (Exception ignored) {
            }
        }
        return "Saved your Claude API key (kept in HelCLI's private storage, not in the config files)."
                + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? " The clipboard was cleared." : " Clear your clipboard now.")
                + Tuils.NEWLINE + "Try: ask what's a good name for a terminal launcher?";
    }


    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_ask;
    }

}
