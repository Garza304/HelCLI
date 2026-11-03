# HelCLI

A Linux-style command line launcher for Android. HelCLI is a fork of
[T-UI Linux CLI Launcher](https://github.com/fandreuz/TUI-ConsoleLauncher) by Francesco Andreuzzi,
picking up where it left off: the lost theme gallery is back, and settings and weather work
again on modern Android.

HelCLI has its own app ID (`com.garza304.helcli`), so it installs next to T-UI instead of replacing it.

## What's new in HelCLI

### 🎨 The theme gallery is back

T-UI used to download themes from a community website that has since gone offline, so
`theme -apply` stopped working. HelCLI keeps its gallery in this repository instead
([browse it with pictures](themes/README.md)) and ships every theme inside the app, so it works offline.

| Command | What it does |
| --- | --- |
| `theme -list` | Show every theme, including new ones added to the online gallery |
| `theme -preview dracula` | See a theme before applying it |
| `theme -apply dracula` | Apply a theme (or a `https://` link to a theme file) |
| `theme -old` | Go back to the theme you had before |
| `theme -export mytheme` | Save your current colors as a theme |
| `theme -share mytheme` | Send a theme file to a friend or another app |
| `theme -import` | Pick a theme file on your phone (or `theme -import https://...`) |
| `theme -delete mytheme` | Delete a theme you saved |

It starts with 24 themes: the original T-UI look, the six 2026 presets, retro monitors (amber, green
phosphor), popular coding palettes (Dracula, Gruvbox, Nord, Solarized, Monokai, Tokyo Night,
Catppuccin), and High Contrast, OLED Black, Paper, Ocean, Sakura, Forest and Vaporwave.
Adding a theme is one JSON file: see [the gallery page](themes/README.md#theme-file-format).

### 📝 Built-in config editor

The 2026 update moved the config files into Android's private app storage, where most file
managers can't reach them. `config -edit theme.xml` (or `ui.xml`, `behavior.xml`, ...) opens them in
an editor inside HelCLI. It has find, undo and Ctrl+S, and it checks the XML before saving, pointing
at the line with the mistake, so a typo can't break the launcher. HelCLI reloads when you close it.

### 🔄 `git pull` your settings

Keep your settings in a GitHub folder and pull them onto the phone. Change how HelCLI looks from
any computer, then see it on the phone a few seconds later.

```
git remote Garza304/HelCLI/my-config   # once: where your settings live
git pull                               # apply the newest version and reload
git undo                               # put back the settings from before the last pull
git status                             # remote and last pull
```

A file only needs the settings you want to control: a `ui.xml` with one line changes one setting.
Every value is checked before it's applied, so a typo is skipped with a warning instead of
breaking the launcher. Pulls `theme.xml`, `ui.xml`, `behavior.xml`, `suggestions.xml`,
`toolbar.xml`, `cmd.xml`, `alias.txt` and `themes/*.json`. See [my-config/](my-config/README.md)
to get started. Works with any public repository and no account. It isn't full git: it
downloads the files at the newest commit.

### Weather

Weather now comes from [Open-Meteo](https://open-meteo.com): free, with no API key or account.

* `tuiweather -location Berlin`, `tuiweather -location San Antonio, Texas`, or `tuiweather -location 29.42,-98.49`
* `tuiweather -location auto` uses the phone's location
* `tuiweather -update` refreshes it now

Your existing `weather_format` keeps working: `%main`, `%description`, `%temp`, `%feels_like`,
`%temp_min`, `%temp_max`, `%humidity`, `%pressure`, `%speed`, `%deg`, `%all` (clouds),
`%precipitation` and `%name` are all filled in. `weather_temperature_measure` still picks
`metric`, `imperial` or `standard` (Kelvin). Old OpenWeatherMap city IDs can't be used; switch to a
city name or coordinates.

### Building

```
./gradlew assembleFdroidDebug          # app/build/outputs/apk/fdroid/debug/
./gradlew testFdroidDebugUnitTest      # unit tests
python3 themes/tools/build_gallery.py  # after adding or changing a theme
```

Every push builds a debug APK on GitHub Actions (see the Actions tab, "helcli-debug-apk").
Release signing is used only when a keystore is set up in `local.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).

---

## Earlier changes (the 2026 T-UI update)

These updates ensure the launcher remains functional, secure, and performant on modern Android devices (Android 11 through Android 14+).

> **Pro Tip:** On the very first install, if background transparency does not take effect immediately, simply type \`restart\` in the terminal and press enter.

### ⌨️ New Commands
*   **`username [user] [device]`**: Instantly customize your terminal prompt. Changes both the username and device name and reloads the UI to apply.
*   **`theme -preset [name]`**: Rapidly switch between high-quality pre-configured themes. (In HelCLI these are gallery themes, and `-preset` is the same as `-apply`.)
    *   **Available Presets:** `blue`, `red`, `green`, `pink`, `bw`, `cyberpunk`.
    *   **Smart Suggestions:** Applying a preset automatically colors the suggestion bar and shortcut buttons to match the aesthetic.
*   **`bbman`**: The new BusyBox manager for installing and verifying Linux binaries.

### ✨ Enhanced Features
*   **Built-in BusyBox Manager:** Gain access to 300+ Linux commands (ls, grep, awk, top, etc.) via the new `bbman -install` command.
*   **Theme Preset Shortcut Buttons:** Enhanced the `theme -preset` command to show interactive shortcut buttons for presets.
*   **Synchronized Theme UI:** Applying a preset now automatically colors the shortcut buttons (suggestions) to match the overall theme.
*   **One-Tap Application:** Shortcut buttons for theme presets execute immediately upon clicking.

---

## 🐧 BusyBox Integration

To enable a full Linux environment, you can install BusyBox directly from the launcher:

1.  Type `bbman -install` in the terminal.
2.  The launcher will automatically detect your architecture, download the verified binary, and check its integrity.
3.  Once finished, you can run any Linux command directly (e.g., `ls`, `ping`, `vi`).
4.  To remove it at any time, use `bbman -remove`.

**Security Note:** Binaries are sourced from the trusted EXALAB repository and are verified against hardcoded SHA-256 hashes to ensure they have not been tampered with.

---

## 🛠 Modern Build System
*   **Target SDK:** Updated to **API 34 (Android 14)**.
*   **Min SDK:** API 21 (Android 5.0).
*   **AndroidX Migration:** Fully migrated from legacy Support Libraries to **AndroidX**.
*   **Gradle & AGP:** Updated to Gradle 8.2 and Android Gradle Plugin 8.2.0.
*   **Java Compatibility:** Built with **Java 17** support.

---

## 🛡 Security Hardening (OWASP MASVS Compliance)

This project has been audited and hardened following the **OWASP Mobile Application Security Verification Standard (MASVS)**.

### 📦 MASVS-STORAGE: Data Storage and Privacy
*   **Scoped Storage Implementation:** All application data has been moved from public external storage (`/sdcard/t-ui/`) to secure, app-private **Scoped Storage** (`Context.getExternalFilesDir()`). This prevents other applications from accessing your T-UI configuration and logs.
*   **Backup Protection:** `android:allowBackup` is set to `false` to prevent sensitive data extraction via ADB backups (MASVS-STORAGE-1).
*   **Secure File Sharing:** Uses `FileProvider` for secure, permission-based file sharing instead of vulnerable `file://` URIs.

### 🌐 MASVS-NETWORK: Network Communication
*   **Enforced TLS:** `android:usesCleartextTraffic` is disabled globally. All network communications are forced over **HTTPS** (TLS 1.2+).
*   **Hardened Service Endpoints:** Internal services (Weather API, Connectivity checks) have been upgraded to secure HTTPS endpoints (MASVS-NETWORK-1).

### ⚙️ MASVS-PLATFORM: Platform Interaction
*   **Signature-Level Protection:** Implemented a custom permission `com.garza304.helcli.permission.RECEIVE_CMD` with `protectionLevel="signature"`. This ensures only apps signed with the same developer key can programmatically send commands to the launcher.
*   **Intent Security:** All system-bound `PendingIntents` use the `FLAG_IMMUTABLE` flag to prevent intent redirection attacks (Android 12+ requirement).
*   **Receiver Security:** All Broadcast Receivers are registered with appropriate export flags (`RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED`) to prevent unauthorized external triggers.

### 🛠 MASVS-CODE: Code Quality & Build Settings
*   **Minification & Obfuscation:** Release builds have R8/Proguard enabled (`minifyEnabled true`) to shrink resources and obfuscate code, making reverse engineering more difficult (MASVS-RESILIENCE-1).
*   **Foreground Service Security:** Updated to comply with Android 14's strict foreground service types (`specialUse`, `mediaPlayback`).

---

## 🔗 Useful links

**Original T-UI community**&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[Reddit](https://www.reddit.com/r/tui_launcher/)**<br>
**Original T-UI group**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[Telegram](https://t.me/tuilauncher)**<br>
**Original T-UI wiki**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[GitHub.com](https://github.com/Andre1299/TUI-ConsoleLauncher/wiki)**<br>

## 📚 Open Source Libraries
* [**CompareString2**](https://github.com/fAndreuzzi/CompareString2)
* [**OkHttp**](https://github.com/square/okhttp)
* [**HTML cleaner**](http://htmlcleaner.sourceforge.net/)
* [**JsonPath**](https://github.com/json-path/JsonPath)
* [**jsoup**](https://github.com/jhy/jsoup/)
