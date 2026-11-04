package ohi.andre.consolelauncher.managers.claude;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a Claude key in what was copied. Keys are read from the clipboard so they never show on screen
 * or in the command history. The copied text may hold more than the key: a label ("API key: ..."),
 * quotes, ANTHROPIC_API_KEY=..., a line break inside a long key, invisible characters.
 */
public class ClipboardKey {

    private ClipboardKey() {}

    private static final Pattern KEY = Pattern.compile("sk-ant-[a-z]+[0-9]*-[A-Za-z0-9_-]{20,}");
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF\\u00AD]");

    /** The first Claude key in the text, or null. Plain Java, so it can be unit tested. */
    public static String find(CharSequence text) {
        if (text == null) return null;
        String s = INVISIBLE.matcher(text).replaceAll("");
        Matcher m = KEY.matcher(s);
        if (m.find()) return m.group();
//        a key broken over two lines
        m = KEY.matcher(s.replaceAll("\\s+", ""));
        return m.find() ? m.group() : null;
    }

    /** What's on the clipboard as text, or null if HelCLI can't read it or it's empty. */
    public static String read(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
//            coerceToText also reads text that was copied with formatting
            CharSequence text = clip.getItemAt(0).coerceToText(context);
            return text == null || text.length() == 0 ? null : text.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** A short description of what was copied when no key was found, without showing it. */
    public static String describe(String clip) {
        if (clip == null) return "HelCLI found nothing on the clipboard (or Android didn't let it read the clipboard).";
        String t = clip.trim();
        if (t.startsWith("sk-ant-")) {
            return "The clipboard starts like a Claude key but isn't a whole one (" + t.length() + " characters). Copy the key again.";
        }
        return "The clipboard has " + t.length() + " characters of text, but no Claude key (sk-ant-...) in it.";
    }

    /** Empties the clipboard after a key was saved. False when Android is too old to do it. */
    public static boolean clear(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        try {
            ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).clearPrimaryClip();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
