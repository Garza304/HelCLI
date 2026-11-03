package ohi.andre.consolelauncher.commands.configeditor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * A simple full-screen editor for HelCLI's config files.
 *
 * Since Android 11 the config folder can't be opened by most file managers, so this lets people
 * keep hand-editing theme.xml, ui.xml and the rest. XML files are checked before saving, so a typo
 * can't break the launcher; the editor points at the line with the problem instead.
 */
public class ConfigEditorActivity extends Activity {

    public static final String PATH = "path";
    public static final String FILE_NAME = "fileName";
    public static final int RESULT_SAVED = 7;

    private static final long MAX_SIZE = 2 * 1024 * 1024;

    private File file;
    private EditText editor, findField;
    private TextView status;

    private String savedText = "";
    private boolean savedOnce = false, discardArmed = false;

    private int outputColor, inputColor, errorColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(PATH);
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);

        try {
            XMLPrefsManager.loadCommons(this);
        } catch (Exception e) {
            Tuils.log(e);
        }

        outputColor = XMLPrefsManager.getColor(Theme.output_color);
        inputColor = XMLPrefsManager.getColor(Theme.input_color);
        errorColor = Color.parseColor("#ff5252");
        int titleColor = XMLPrefsManager.getColor(Theme.device_color);
        int textSize = XMLPrefsManager.getInt(Ui.input_output_size);
        Typeface typeface = Tuils.getTypeface(this);

        int background = XMLPrefsManager.getColor(Theme.bg_color);
//        a fully transparent background would show the activity behind it, so fall back to black
        if (Color.alpha(background) < 0xff) background = Color.BLACK;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !XMLPrefsManager.getBoolean(Ui.ignore_bar_color)) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(XMLPrefsManager.getColor(Theme.statusbar_color));
            window.setNavigationBarColor(XMLPrefsManager.getColor(Theme.navigationbar_color));
        }

        int pad = dp(8);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);
        root.setPadding(pad, pad, pad, pad);
        root.setFitsSystemWindows(true);

//        title and buttons
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(file.getName(), titleColor, textSize, typeface);
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(button("save", textSize, typeface, v -> save()));
        bar.addView(button("undo", textSize, typeface, v -> undo()));
        bar.addView(button("close", textSize, typeface, v -> close()));
        root.addView(bar);

//        find
        LinearLayout findBar = new LinearLayout(this);
        findBar.setOrientation(LinearLayout.HORIZONTAL);
        findBar.setGravity(Gravity.CENTER_VERTICAL);

        findField = new EditText(this);
        findField.setTypeface(typeface);
        findField.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        findField.setTextColor(inputColor);
        findField.setHintTextColor(withAlpha(outputColor, 0x80));
        findField.setHint("find...");
        findField.setSingleLine(true);
        findField.setBackgroundColor(Color.TRANSPARENT);
        findField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        findField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        findField.setOnEditorActionListener((v, actionId, event) -> {
            findNext();
            return true;
        });
        findBar.addView(findField, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        findBar.addView(button("next", textSize, typeface, v -> findNext()));
        root.addView(findBar);

//        the file
        editor = new EditText(this);
        editor.setTypeface(typeface);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        editor.setTextColor(outputColor);
        editor.setBackgroundColor(Color.TRANSPARENT);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setVerticalScrollBarEnabled(true);
        editor.setPadding(0, pad, 0, pad);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) editor.setTextCursorDrawable(null);
        editor.setHighlightColor(withAlpha(inputColor, 0x60));
        root.addView(editor, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        status = text("", outputColor, Math.max(10, textSize - 2), typeface);
        root.addView(status);

        setContentView(root);

        String text = load();
        if (text == null) return;
        savedText = text;
        editor.setText(text);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                discardArmed = false;
            }
        });

        setStatus(isXml() ? "Edit the value=\"...\" parts, then tap save. Mistakes are caught before saving."
                : "Tap save when you're done.", false);
    }

    private String load() {
        if (!file.exists()) {
            setStatus("This file doesn't exist yet. It will be created when you save.", false);
            return "";
        }
        if (file.length() > MAX_SIZE) {
            setStatus("This file is too big to edit here.", true);
            editor.setEnabled(false);
            return null;
        }

        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) break;
                read += n;
            }
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        } catch (IOException e) {
            setStatus("Couldn't read the file: " + e.getMessage(), true);
            editor.setEnabled(false);
            return null;
        }
    }

    private boolean isXml() {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    private void save() {
        String text = editor.getText().toString();

        if (isXml()) {
            String problem = checkXml(text);
            if (problem != null) {
                setStatus(problem, true);
                return;
            }
        }

//        write to a temporary file first, so a failed write can't leave half a config file
        File tmp = new File(file.getParentFile(), "." + file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            setStatus("Couldn't save: " + e.getMessage(), true);
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(file)) {
            file.delete();
            if (!tmp.renameTo(file)) {
                setStatus("Couldn't save the file", true);
                return;
            }
        }

        savedText = text;
        savedOnce = true;
        setStatus("Saved. HelCLI will reload when you close the editor.", false);
    }

    /** Returns a readable description of the first XML problem, and moves the cursor to it. */
    private String checkXml(String text) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
            return null;
        } catch (SAXParseException e) {
            int line = e.getLineNumber();
            if (line > 0) moveToLine(line);
            return "Not saved. Line " + line + ": " + e.getMessage();
        } catch (Exception e) {
            return "Not saved: " + e.getMessage();
        }
    }

    private void moveToLine(int line) {
        String text = editor.getText().toString();
        int index = 0;
        for (int l = 1; l < line && index >= 0; l++) {
            index = text.indexOf('\n', index);
            if (index >= 0) index++;
        }
        if (index < 0) index = text.length();
        int end = text.indexOf('\n', index);
        if (end < 0) end = text.length();
        editor.requestFocus();
        editor.setSelection(index, end);
    }

    private void findNext() {
        String query = findField.getText().toString();
        if (query.length() == 0) return;

        String text = editor.getText().toString().toLowerCase(Locale.ROOT);
        String q = query.toLowerCase(Locale.ROOT);
        int from = Math.max(editor.getSelectionEnd(), 0);
        int index = text.indexOf(q, from);
        if (index < 0) index = text.indexOf(q);

        if (index < 0) {
            setStatus("\"" + query + "\" isn't in this file", true);
            return;
        }
        editor.requestFocus();
        editor.setSelection(index, index + q.length());
        setStatus("", false);
    }

    private void undo() {
        editor.setText(savedText);
        setStatus("Changes since the last save were undone", false);
    }

    private void close() {
        boolean changed = !editor.getText().toString().equals(savedText);
        if (changed && !discardArmed) {
            discardArmed = true;
            setStatus("You have unsaved changes. Tap save, or close again to throw them away.", true);
            return;
        }

        if (savedOnce) {
            Intent result = new Intent();
            result.putExtra(FILE_NAME, file.getName());
            setResult(RESULT_SAVED, result);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        ctrl+s saves, for hardware keyboards
        if (keyCode == KeyEvent.KEYCODE_S && event.isCtrlPressed()) {
            save();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void setStatus(String s, boolean isError) {
        status.setText(s);
        status.setTextColor(isError ? errorColor : outputColor);
        status.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private TextView text(String s, int color, int size, Typeface typeface) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTypeface(typeface);
        return t;
    }

    private TextView button(String label, int size, Typeface typeface, View.OnClickListener listener) {
        TextView b = text("[" + label + "]", inputColor, size, typeface);
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        b.setOnClickListener(listener);
        b.setContentDescription(label);
        return b;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }
}
