package ohi.andre.consolelauncher.managers.sync;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import ohi.andre.consolelauncher.managers.ThemeManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

/**
 * "reset": puts every setting (theme.xml, ui.xml, behavior.xml, suggestions.xml, toolbar.xml, cmd.xml)
 * back to HelCLI's defaults.
 *
 * HelCLI writes every setting into its files the first time it runs, so after an update the files still
 * hold the old defaults; this is how to get the new ones. A few personal settings are kept (see KEEP),
 * and so are aliases, notes, apps and saved themes, which live in other files. The old files are copied
 * to "reset-backup" first, so "reset undo" can put them back.
 */
public class SettingsReset {

    private SettingsReset() {}

    public static final String BACKUP_DIR = "reset-backup";

    /** Settings about you rather than how HelCLI looks: a reset leaves them as they are. */
    public static final XMLPrefsSave[] KEEP = {
            Behavior.weather_location,
            Behavior.weather_temperature_measure,
            Behavior.weather_key,
            Behavior.claude_plan_source,
            Behavior.claude_budget_usd,
            Ui.username,
    };

    /** What "reset" says before doing anything. */
    public static String explain() {
        StringBuilder kept = new StringBuilder();
        for (XMLPrefsSave s : KEEP) {
            if (kept.length() > 0) kept.append(", ");
            kept.append(s.label());
        }
        return "reset now -> puts every setting back to HelCLI's defaults (theme, layout, text and colors at the top"
                + " of the screen, suggestions, toolbar), then restarts HelCLI."
                + Tuils.NEWLINE + "Kept as they are: " + kept + "; your aliases, notes, apps and saved themes."
                + Tuils.NEWLINE + "A backup is saved first: reset undo puts your settings back.";
    }

    /** Resets the settings and restarts HelCLI. Returns a message, or null when the restart reports instead. */
    public static String reset(Context context, Reloadable reloadable) {
        File folder = Tuils.getFolder();
        if (folder == null) return "reset: can't find HelCLI's folder";

        File backup = new File(folder, BACKUP_DIR);
        Tuils.delete(backup);
        if (!backup.mkdirs()) return "reset: couldn't make a backup folder, nothing was changed";

//        read what to keep before the files go away
        List<XMLPrefsSave> keep = new ArrayList<>();
        List<String> keepValues = new ArrayList<>();
        for (XMLPrefsSave s : KEEP) {
            String value = XMLPrefsManager.get(s);
            if (value != null && !value.equals(s.defaultValue())) {
                keep.add(s);
                keepValues.add(value);
            }
        }

        int reset = 0;
        for (XMLPrefsManager.XMLPrefsRoot root : XMLPrefsManager.XMLPrefsRoot.values()) {
            File file = new File(folder, root.path);
            try {
                if (file.exists()) copy(file, new File(backup, root.path));
            } catch (IOException e) {
                Tuils.delete(backup);
                return "reset: couldn't back up " + root.path + " (" + e.getMessage() + "), nothing was changed";
            }
//            an empty file: HelCLI fills in every default when it starts
            if (XMLPrefsManager.resetFile(file, root.name())) reset++;
        }

        for (int i = 0; i < keep.size(); i++) {
            XMLPrefsSave s = keep.get(i);
            XMLPrefsManager.set(new File(folder, s.parent().path()), s.label(),
                    new String[] {XMLPrefsManager.VALUE_ATTRIBUTE}, new String[] {keepValues.get(i)});
        }

        ThemeManager.forgetName(context);

        reloadable.addMessage("reset: " + reset + " settings files back to the defaults"
                + (keep.isEmpty() ? "" : ", kept " + keep.size() + " personal " + (keep.size() == 1 ? "setting" : "settings"))
                + " (reset undo puts them back)", null);
        reloadable.reload();
        return null;
    }

    /** Puts back the files saved by the last reset. */
    public static String undo(Context context, Reloadable reloadable) {
        File folder = Tuils.getFolder();
        File backup = folder == null ? null : new File(folder, BACKUP_DIR);
        File[] files = backup == null ? null : backup.listFiles();
        if (files == null || files.length == 0) return "Nothing to undo: there's no backup from a reset";

        int restored = 0;
        for (File f : files) {
            try {
                copy(f, new File(folder, f.getName()));
                restored++;
            } catch (IOException e) {
                return "reset undo: couldn't restore " + f.getName() + " (" + e.getMessage() + ")";
            }
        }
        Tuils.delete(backup);

        reloadable.addMessage("reset undo: restored " + restored + (restored == 1 ? " file" : " files") + " from before the reset", null);
        reloadable.reload();
        return null;
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
    }
}
