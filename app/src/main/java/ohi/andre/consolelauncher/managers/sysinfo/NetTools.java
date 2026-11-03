package ohi.andre.consolelauncher.managers.sysinfo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.tuils.Tuils;

/** What the ip, ping and dns commands do. Call everything here off the main thread. */
public class NetTools {

    private NetTools() {}

    /** Host names and IP addresses: letters, digits, dots, dashes and colons (IPv6) only. */
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.:-]{1,253}");

    public static boolean validHost(String host) {
        return host != null && HOST.matcher(host).matches() && !host.startsWith("-");
    }

    public interface Output {
        void line(String text);
    }

//    ---------------------------------------------------------------- ip

    public static String ip(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "Needs Android 6 or later";

        Network n = cm.getActiveNetwork();
        if (n == null) return "Not connected";
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        LinkProperties lp = cm.getLinkProperties(n);

        StringBuilder b = new StringBuilder();
        String type = "Network";
        if (caps != null) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) type = "VPN";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type = "WiFi";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) type = "Mobile data";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) type = "Ethernet";
        }
        b.append(type);

        if (type.equals("WiFi")) {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm != null ? wm.getConnectionInfo() : null;
            if (wi != null) {
                String ssid = wi.getSSID();
//                the name needs location access; without it Android says <unknown ssid>
                if (ssid != null && !ssid.contains("unknown")) b.append(' ').append(ssid.replace("\"", ""));
                b.append(String.format(Locale.US, "  %d dBm, %d Mbps", wi.getRssi(), wi.getLinkSpeed()));
            }
        }
        b.append('\n');

        if (caps != null) {
            b.append("  Internet  ").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "working" : "not working (or a login page)").append('\n');
            b.append("  Metered   ").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ? "no" : "yes").append('\n');
        }
        if (lp != null) {
            b.append("  Interface ").append(lp.getInterfaceName()).append('\n');
            for (LinkAddress a : lp.getLinkAddresses()) {
                InetAddress addr = a.getAddress();
                b.append("  ").append(addr instanceof Inet4Address ? "IPv4      " : "IPv6      ")
                        .append(addr.getHostAddress()).append('/').append(a.getPrefixLength()).append('\n');
            }
            for (RouteInfo r : lp.getRoutes()) {
                if (r.isDefaultRoute() && r.getGateway() != null && !r.getGateway().isAnyLocalAddress()) {
                    b.append("  Gateway   ").append(r.getGateway().getHostAddress()).append('\n');
                }
            }
            for (InetAddress d : lp.getDnsServers()) b.append("  DNS       ").append(d.getHostAddress()).append('\n');
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lp.isPrivateDnsActive()) {
                b.append("  Private DNS ").append(lp.getPrivateDnsServerName() != null ? lp.getPrivateDnsServerName() : "on").append('\n');
            }
        }
        b.append("Your public IP: ip -public");
        return b.toString();
    }

    /** The address the internet sees, from api.ipify.org (only when asked). */
    public static String publicIp() {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://api.ipify.org").openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("User-Agent", "HelCLI");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                String ip = r.readLine();
                return "Public IP: " + (ip == null ? "unknown" : ip.trim()) + "  (from api.ipify.org)";
            }
        } catch (Exception e) {
            return "Couldn't reach api.ipify.org: " + e.getMessage();
        }
    }

    /** The default gateway of the active network, or null. */
    public static String gateway(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm == null ? null : cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        if (lp == null) return null;
        for (RouteInfo r : lp.getRoutes()) {
            if (r.isDefaultRoute() && r.getGateway() instanceof Inet4Address && !r.getGateway().isAnyLocalAddress()) {
                return r.getGateway().getHostAddress();
            }
        }
        return null;
    }

//    ---------------------------------------------------------------- ping

    /**
     * Pings with the phone's own ping (4 times), writing each line as it comes. Some phones don't
     * let apps send pings; then it times opening a web connection (port 443) instead.
     */
    public static void ping(String host, Output out) {
        if (!validHost(host)) {
            out.line("That isn't a host name or IP address: " + host);
            return;
        }

        boolean worked = false;
        try {
            Process p = new ProcessBuilder("ping", "-c", "4", "-W", "2", host).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                List<String> lines = new ArrayList<>();
                while ((line = r.readLine()) != null) {
                    if (line.trim().length() == 0) continue;
                    lines.add(line);
                    if (line.contains("bytes from") || line.contains("statistics") || line.contains("packet loss") || line.startsWith("rtt") || line.startsWith("round-trip")) {
                        out.line(line);
                        worked = true;
                    }
                }
                int code = p.waitFor();
                if (!worked && code != 0) {
                    String err = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
                    if (err.toLowerCase(Locale.ROOT).contains("unknown host") || err.toLowerCase(Locale.ROOT).contains("bad address")) {
                        out.line("ping: can't find " + host);
                        return;
                    }
                }
                if (worked) return;
            }
        } catch (Exception e) {
            Tuils.log(e);
        }

        tcpPing(host, out);
    }

    private static void tcpPing(String host, Output out) {
        out.line("(ping isn't allowed here: timing a connection to port 443 instead)");
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (Exception e) {
            out.line("ping: can't find " + host);
            return;
        }
        int ok = 0;
        long total = 0;
        for (int i = 1; i <= 4; i++) {
            long start = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(addr, 443), 3000);
                long ms = (System.nanoTime() - start) / 1_000_000;
                total += ms;
                ok++;
                out.line(String.format(Locale.US, "connected to %s:443  time=%d ms", addr.getHostAddress(), ms));
            } catch (Exception e) {
                out.line("no answer from " + addr.getHostAddress() + ":443");
            }
        }
        out.line(String.format(Locale.US, "%d of 4 answered%s", ok, ok > 0 ? ", average " + (total / ok) + " ms" : ""));
    }

//    ---------------------------------------------------------------- dns

    public static String dns(Context context, String host) {
        if (!validHost(host)) return "That isn't a host name or IP address: " + host;
        StringBuilder b = new StringBuilder();
        long start = System.nanoTime();
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            long ms = (System.nanoTime() - start) / 1_000_000;
            b.append(host).append("  (").append(ms).append(" ms)");
            for (InetAddress a : all) {
                b.append("\n  ").append(a instanceof Inet4Address ? "A     " : "AAAA  ").append(a.getHostAddress());
            }
//            an address given: also look up its name
            if (all.length == 1 && host.equals(all[0].getHostAddress())) {
                String name = all[0].getCanonicalHostName();
                b.append("\n  name  ").append(name.equals(host) ? "(none)" : name);
            }
        } catch (Exception e) {
            b.append("Can't find ").append(host);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            if (lp != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lp.isPrivateDnsActive()) {
                    b.append("\nAsked through Private DNS ").append(lp.getPrivateDnsServerName() != null ? lp.getPrivateDnsServerName() : "");
                } else if (!lp.getDnsServers().isEmpty()) {
                    b.append("\nAsked ").append(lp.getDnsServers().get(0).getHostAddress());
                }
            }
        }
        return b.toString();
    }
}
