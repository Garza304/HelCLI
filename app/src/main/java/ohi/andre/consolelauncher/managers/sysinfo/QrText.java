package ohi.andre.consolelauncher.managers.sysinfo;

import java.util.ArrayList;
import java.util.List;

import io.nayuki.qrcodegen.DataTooLongException;
import io.nayuki.qrcodegen.QrCode;
import io.nayuki.qrcodegen.QrSegment;

/**
 * Draws a QR code with block characters, two rows of the code per line of text (▀ ▄ █).
 * Each character is about as wide as half a line is tall, so the modules come out nearly square.
 * The command draws it black on white, which scanners read best. Plain Java, so it can be unit tested.
 */
public class QrText {

    private QrText() {}

    /** Version 5 (37 modules) plus the margin is 41 characters: the most that fits a phone's terminal. */
    public static final int MAX_VERSION = 5;
    /** The light margin around the code, in modules. Scanners want some; 2 keeps it narrow. */
    public static final int QUIET = 2;

    /** Null if the text is too long to draw at a size that fits. */
    public static List<String> render(String text) {
        QrCode qr;
        try {
            qr = QrCode.encodeSegments(QrSegment.makeSegments(text), QrCode.Ecc.LOW, 1, MAX_VERSION, -1, true);
        } catch (DataTooLongException e) {
            return null;
        }
        return render(qr.size, (x, y) -> qr.getModule(x, y));
    }

    public interface Modules {
        boolean dark(int x, int y);
    }

    /** Draws a size x size grid of modules with the margin around it. Dark modules are the ink. */
    public static List<String> render(int size, Modules m) {
        int total = size + 2 * QUIET;
        List<String> lines = new ArrayList<>();
        for (int y = 0; y < total; y += 2) {
            StringBuilder b = new StringBuilder();
            for (int x = 0; x < total; x++) {
                boolean top = dark(m, size, x - QUIET, y - QUIET);
                boolean bottom = y + 1 < total && dark(m, size, x - QUIET, y + 1 - QUIET);
                b.append(top && bottom ? '█' : top ? '▀' : bottom ? '▄' : ' ');
            }
            lines.add(b.toString());
        }
        return lines;
    }

    private static boolean dark(Modules m, int size, int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size && m.dark(x, y);
    }
}
