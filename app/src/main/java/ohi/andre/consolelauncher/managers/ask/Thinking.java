package ohi.andre.consolelauncher.managers.ask;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Locale;

import ohi.andre.consolelauncher.UIManager;

/**
 * The line that shows Claude is working on an answer: a spinner, a word that changes every few
 * seconds, and the time so far. It's one line in the terminal that updates in place, and when the
 * answer comes it's replaced with how long it took.
 */
public class Thinking {

    static final String[] WORDS = {
            "Thinking", "Pondering", "Mulling it over", "Connecting the dots", "Reading the manual",
            "Compiling thoughts", "Grepping memory", "Piping ideas", "Untangling", "Brewing",
            "Sketching an answer", "Checking twice", "Tuning", "Weighing options", "Rummaging",
    };
    static final String SPINNER = "|/-\\";
    static final long FRAME_MS = 200, WORD_MS = 2500;

    private static int next;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String id;
    private final int color;
    private final long started = System.currentTimeMillis();
    private final int firstWord;
    private boolean stopped;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (stopped) return;
            send(frame(System.currentTimeMillis() - started, firstWord), false);
            handler.postDelayed(this, FRAME_MS);
        }
    };

    private Thinking(Context context, int color) {
        this.context = context.getApplicationContext();
        this.color = color;
        synchronized (Thinking.class) {
            id = "ask-" + (next++);
//            each question starts on a different word
            firstWord = next * 7 % WORDS.length;
        }
    }

    /** Shows the line and keeps it moving until {@link #stop}. */
    public static Thinking start(Context context, int color) {
        Thinking t = new Thinking(context, color);
        t.handler.post(t.tick);
        return t;
    }

    /** Replaces the line with how long Claude took. Safe to call from any thread. */
    public void stop() {
        final long took = System.currentTimeMillis() - started;
        handler.post(() -> {
            stopped = true;
            handler.removeCallbacks(tick);
            send(done(took), true);
        });
    }

    private void send(String text, boolean last) {
        Intent i = new Intent(UIManager.ACTION_LIVE_LINE);
        i.putExtra(UIManager.LIVE_ID, id);
        i.putExtra(UIManager.LIVE_TEXT, text);
        i.putExtra(UIManager.LIVE_COLOR, color);
        i.putExtra(UIManager.LIVE_LAST, last);
        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
    }

    /** "[/] Pondering... 4s". Plain Java, so it can be unit tested. */
    static String frame(long elapsedMs, int firstWord) {
        char spin = SPINNER.charAt((int) (elapsedMs / FRAME_MS % SPINNER.length()));
        String word = WORDS[(int) ((firstWord + elapsedMs / WORD_MS) % WORDS.length)];
        return "[" + spin + "] " + word + "... " + seconds(elapsedMs);
    }

    /** "Claude · 6s". */
    static String done(long tookMs) {
        return "Claude · " + seconds(tookMs);
    }

    static String seconds(long ms) {
        long s = ms / 1000;
        return s < 60 ? s + "s" : String.format(Locale.US, "%dm %02ds", s / 60, s % 60);
    }
}
