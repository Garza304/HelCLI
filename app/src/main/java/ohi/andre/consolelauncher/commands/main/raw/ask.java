package ohi.andre.consolelauncher.commands.main.raw;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.main.specific.SimpleParamCommand;
import ohi.andre.consolelauncher.managers.ask.AskKey;
import ohi.andre.consolelauncher.managers.claude.ClaudeUsage;
import ohi.andre.consolelauncher.managers.claude.ClipboardKey;
import ohi.andre.consolelauncher.managers.claude.KeyLink;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * ask [question]: an answer from Claude, in the terminal. Needs a Claude API key (ask -key);
 * each question costs a little on that account.
 */
public class ask extends SimpleParamCommand {

    private static final String PREFS = "helcli_ask", WARNED = "warned";

    public ask() {
        option("link", (pack, t) -> KeyLink.start(pack.context, KeyLink.Kind.API));
        option("key", (pack, t) -> saveKey(pack.context));
        option("forget", (pack, t) -> {
            AskKey.forget(pack.context);
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

//        the one-word forms without a dash still work ("ask key")
        String word = input.trim().toLowerCase(java.util.Locale.ROOT);
        if (word.equals("key")) return saveKey(c);
        if (word.equals("link")) return KeyLink.start(c, KeyLink.Kind.API);

//        the SDK's JSON library needs Android 8
        if (Build.VERSION.SDK_INT < 26) return "ask needs Android 8 or later";

        final String key = AskKey.get(c);
        if (key == null) {
            return "ask needs a Claude API key. Run ask -link to make one in the Claude Console and link it.";
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
        String clip = ClipboardKey.read(c);
        String key = ClipboardKey.find(clip);

        if (key == null) {
            return ClipboardKey.describe(clip) + Tuils.NEWLINE
                    + "Copy your Claude API key (sk-ant-api03-...), then run ask -key again. Or run ask -link to make one.";
        }
        if (key.startsWith("sk-ant-admin")) {
            return "That's an Admin key (" + ClaudeUsage.maskKey(key) + "), which can't ask Claude questions; it's for the usage line"
                    + " (usage -key). ask needs an ordinary API key (sk-ant-api03-...): ask -link makes one.";
        }
        if (!AskKey.looksLikeApiKey(key)) {
            return "That isn't an API key (" + ClaudeUsage.maskKey(key) + "). ask needs one that starts with sk-ant-api03-: ask -link makes one.";
        }
        AskKey.save(c, key);
        boolean cleared = ClipboardKey.clear(c);
        return "Saved your Claude API key " + ClaudeUsage.maskKey(key) + " (kept in HelCLI's private storage, not in the config files)."
                + (cleared ? " The clipboard was cleared." : " Clear your clipboard now.")
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
