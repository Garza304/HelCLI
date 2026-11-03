# HelCLI's signing key

Android only installs an update when it's signed with the same key as the installed app. Every
GitHub build signs HelCLI with this one permanent key, so new versions install over old ones and
keep your settings.

`helcli.jks.enc` is the key, encrypted (AES-256, PBKDF2 with 200,000 rounds). The build decrypts it
with the `HELCLI_KEY_PASSWORD` repository secret (Settings > Secrets and variables > Actions).

Why it's encrypted: HelCLI accepts commands from other apps signed with the same key
(`RECEIVE_CMD` is a signature permission), so a public key would let anyone build an app that
controls it.

Keep the password somewhere safe (a password manager). Without it, builds can't be signed with this
key, and the next install would need an uninstall first.

To decrypt it on your own computer, for signed local builds:

```
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in signing/helcli.jks.enc -out helcli.jks
```

then put `storeFile=helcli.jks`, `storePassword`, `keyAlias=helcli` and `keyPassword` (the same
password) in `local.properties`. Both files are ignored by git.

Certificate SHA-256: `04:B6:BD:50:EC:AD:B9:8F:81:B9:92:9E:19:11:80:48:E0:CB:56:8C:D8:C7:63:21:42:07:D0:6A:F2:F7:BA:6B`
