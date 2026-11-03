package ohi.andre.consolelauncher.commands.main.raw;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.ScaleXSpan;

import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.sysinfo.QrText;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * qr [text]: draws a scannable QR code in the terminal. Without text it uses the clipboard.
 *
 * It's drawn black on white (what scanners read best) in the built-in font, whose block characters
 * fill the whole line height so rows touch, and squeezed sideways so each module is square.
 * Both were checked by decoding screenshots of the output.
 */
public class qr implements CommandAbstraction {

    /** Half a line is 1024 font units tall and a character 1234 wide: this makes modules square. */
    private static final float SQUARE = 1024f / 1234f;

    private static Typeface lucida;

    @Override
    public String exec(ExecutePack pack) {
        return draw(pack.context, pack.getString());
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        ClipboardManager cm = (ClipboardManager) pack.context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm == null ? null : cm.getPrimaryClip();
        CharSequence text = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).getText() : null;
        if (text == null || text.toString().trim().length() == 0) {
            return "Usage: qr [text], or copy something and run qr";
        }
        return draw(pack.context, text.toString());
    }

    private static String draw(Context context, String text) {
        text = text.trim();
        List<String> lines = QrText.render(text);
        if (lines == null) return "That's too long to draw on this screen (up to about 100 characters fit)";

        if (lucida == null) {
            try {
                lucida = Typeface.createFromAsset(context.getAssets(), "lucida_console.ttf");
            } catch (Exception e) {
                lucida = Typeface.MONOSPACE;
            }
        }

        SpannableStringBuilder b = new SpannableStringBuilder();
        for (String line : lines) {
            if (b.length() > 0) b.append('\n');
            int start = b.length();
            b.append(line);
            int end = b.length();
            b.setSpan(new BackgroundColorSpan(Color.WHITE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(Color.BLACK), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ScaleXSpan(SQUARE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new FontSpan(lucida), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String shown = text.length() > 40 ? text.substring(0, 39) + "…" : text;
        b.append('\n').append(shown);

        Tuils.sendOutput(context, b, TerminalManager.CATEGORY_NO_COLOR);
        return null;
    }

    /** Draws with a given typeface (TypefaceSpan(Typeface) needs Android 9). */
    private static class FontSpan extends MetricAffectingSpan {
        private final Typeface typeface;

        FontSpan(Typeface typeface) {
            this.typeface = typeface;
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            p.setTypeface(typeface);
        }

        @Override
        public void updateDrawState(TextPaint p) {
            p.setTypeface(typeface);
        }
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_qr;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return null;
    }
}
