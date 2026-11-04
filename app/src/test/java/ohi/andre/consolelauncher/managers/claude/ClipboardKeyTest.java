package ohi.andre.consolelauncher.managers.claude;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ClipboardKeyTest {

    private static final String KEY = "sk-ant-api03-Abc_def-1234567890abcdefghijKLMNOP_qrstuvwxyzAA";

    @Test
    public void findsAKeyOnItsOwn() {
        assertEquals(KEY, ClipboardKey.find(KEY));
        assertEquals(KEY, ClipboardKey.find("  " + KEY + "\n"));
    }

    @Test
    public void findsAKeyWithOtherTextAroundIt() {
        assertEquals(KEY, ClipboardKey.find("API key: " + KEY));
        assertEquals(KEY, ClipboardKey.find("\"" + KEY + "\""));
        assertEquals(KEY, ClipboardKey.find("ANTHROPIC_API_KEY=" + KEY));
        assertEquals(KEY, ClipboardKey.find("HelCLI\n" + KEY + "\ncreated Nov 4"));
    }

    @Test
    public void findsAKeyWithInvisibleCharactersOrALineBreak() {
        assertEquals(KEY, ClipboardKey.find("﻿" + KEY + "​"));
        assertEquals(KEY, ClipboardKey.find(KEY.substring(0, 30) + "\n" + KEY.substring(30)));
    }

    @Test
    public void findsAdminKeys() {
        String admin = "sk-ant-admin01-" + "x".repeat(40);
        assertEquals(admin, ClipboardKey.find("key: " + admin));
    }

    @Test
    public void noKey() {
        assertNull(ClipboardKey.find(null));
        assertNull(ClipboardKey.find("hello"));
        assertNull(ClipboardKey.find("sk-ant-api03-tooshort"));
    }
}
