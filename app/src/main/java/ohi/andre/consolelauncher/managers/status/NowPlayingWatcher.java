package ohi.andre.consolelauncher.managers.status;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.SystemClock;

import java.util.List;

import ohi.andre.consolelauncher.managers.notifications.NotificationService;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Follows whatever is playing in any app (Spotify, YouTube Music, podcasts, ...) through Android's
 * media sessions. Android only shares them with apps that have notification access, which HelCLI
 * already asks for to show notifications.
 */
public class NowPlayingWatcher {

    public interface Listener {
        void onText(String text);
    }

    private final Context context;
    private final Handler handler;
    private final Listener listener;
    private final String format;
    private final boolean showPaused;
    private final boolean ticks;

    private MediaSessionManager sessions;
    private ComponentName component;
    private MediaController current;
    private boolean stopped;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            render();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            render();
        }

        @Override
        public void onSessionDestroyed() {
            pick(null);
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::pick;

//    redraws once a second while playing, when the format shows the position or a progress bar
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
        }
    };

    public NowPlayingWatcher(Context context, Handler handler, String format, boolean showPaused, Listener listener) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.format = format;
        this.showPaused = showPaused;
        this.listener = listener;
        this.ticks = format.contains("%pos") || format.contains("%bar");
    }

    public void start() {
        sessions = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        component = new ComponentName(context, NotificationService.class);
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, component, handler);
            pick(sessions.getActiveSessions(component));
        } catch (SecurityException e) {
            listener.onText("♪ Now playing needs notification access: notifications -access");
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public void stop() {
        stopped = true;
        handler.removeCallbacks(tick);
        try {
            if (sessions != null) sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        } catch (Exception ignored) {
        }
        if (current != null) current.unregisterCallback(callback);
        current = null;
    }

    /** Follows the session that's playing, or else the most recent one. */
    private void pick(List<MediaController> controllers) {
        if (stopped) return;
        if (controllers == null) {
            try {
                controllers = sessions.getActiveSessions(component);
            } catch (Exception e) {
                controllers = null;
            }
        }

        MediaController best = null;
        if (controllers != null) {
            for (MediaController c : controllers) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
            }
            if (best == null && !controllers.isEmpty()) best = controllers.get(0);
        }

        if (current != null && (best == null || !best.getSessionToken().equals(current.getSessionToken()))) {
            current.unregisterCallback(callback);
            current = null;
        }
        if (best != null && current == null) {
            current = best;
            current.registerCallback(callback, handler);
        }
        render();
    }

    private void render() {
        handler.removeCallbacks(tick);
        if (stopped) return;

        MediaController c = current;
        MediaMetadata meta = c == null ? null : c.getMetadata();
        PlaybackState state = c == null ? null : c.getPlaybackState();
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;

        if (meta == null || (!playing && !showPaused)) {
            listener.onText("");
            return;
        }

        String title = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null || artist.length() == 0) artist = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        String album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM);
        long duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION);

        long position = -1;
        if (state != null && state.getPosition() >= 0) {
            position = state.getPosition();
            if (playing) {
                position += (long) ((SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime()) * state.getPlaybackSpeed());
            }
            if (duration > 0) position = Math.min(position, duration);
        }

        listener.onText(StatusFormats.nowPlaying(format, title, artist, album, appName(c.getPackageName()),
                playing, position, duration));

        if (playing && ticks) handler.postDelayed(tick, 1000);
    }

    private String appName(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }
}
