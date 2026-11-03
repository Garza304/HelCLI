package ohi.andre.consolelauncher.managers.claude;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeyLinkTest {

    private static final String ADMIN = "sk-ant-admin01-abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String API = "sk-ant-api03-abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String ENTERPRISE = "sk-ant-api01-abcdefghijklmnopqrstuvwxyz0123456789";

    @Test
    public void usageTakesAdminAndEnterpriseKeys() {
        assertEquals(KeyLink.Found.KEY, KeyLink.classify(KeyLink.Kind.ADMIN, ADMIN));
        assertEquals(KeyLink.Found.KEY, KeyLink.classify(KeyLink.Kind.ADMIN, "  " + ENTERPRISE + "\n"));
        assertEquals(KeyLink.Found.OTHER_KIND, KeyLink.classify(KeyLink.Kind.ADMIN, API));
    }

    @Test
    public void askTakesOrdinaryApiKeys() {
        assertEquals(KeyLink.Found.KEY, KeyLink.classify(KeyLink.Kind.API, API));
        assertEquals(KeyLink.Found.OTHER_KIND, KeyLink.classify(KeyLink.Kind.API, ADMIN));
    }

    @Test
    public void anythingElseIsNothing() {
        assertEquals(KeyLink.Found.NOTHING, KeyLink.classify(KeyLink.Kind.API, null));
        assertEquals(KeyLink.Found.NOTHING, KeyLink.classify(KeyLink.Kind.ADMIN, "https://platform.claude.com/settings/keys"));
        assertEquals(KeyLink.Found.NOTHING, KeyLink.classify(KeyLink.Kind.API, "sk-ant-api03-short"));
    }
}
