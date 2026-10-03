# StatusBar Hider

A minimal Android app that hides the status bar system-wide (including the launcher) using [Shizuku](https://shizuku.rikka.app/). No system apps are uninstalled. Swipe-down to expand notifications is preserved.

The app reads OEM-specific commands from JSON files in `app/src/main/assets/oem/`. Supported configs: **HyperOS/MIUI** (tested), plus researched defaults for **Samsung One UI, Google Pixel, OnePlus, OPPO, realme, vivo/iQOO, Motorola, Nothing, Huawei, Honor, Sony, ASUS, Nokia, Tecno, Infinix, ZTE/nubia** and a generic **AOSP** fallback. All of them except HyperOS are flagged `untested` (shown in the UI) - see [CONTRIBUTING.md](CONTRIBUTING.md) to fix and promote one.

## Prerequisites

- A device running a supported OEM skin
- [Shizuku](https://shizuku.rikka.app/) installed and running on the device
  - Start via `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
  - Or pair wirelessly; enable auto-start on boot if needed

## Quick start

```bash
mise install            # install dev tooling (pre-commit, ktlint)
mise run install        # build + install release APK on device
```

Building needs a **JDK 21** and an **Android SDK** (`ANDROID_HOME`) with the
`platforms;android-37.0` and `build-tools;37.0.0` packages - neither is
installed by mise.

## Mise tasks

| Task                      | Description                      |
|---------------------------|----------------------------------|
| `mise run build`          | Build signed release APK         |
| `mise run debug`          | Build debug APK                  |
| `mise run install`        | Build + install release APK      |
| `mise run install-debug`  | Build + install debug APK        |
| `mise run clean`          | Clean Gradle outputs             |
| `mise run devices`        | List connected ADB devices       |
| `mise run logs`           | Live log TUI (logstream)         |
| `mise run show-in-drawer` | Re-enable the launcher icon      |
| `mise run uninstall`      | Uninstall the app from device    |

## Build without mise

```bash
./gradlew assembleRelease
```

Requires `ANDROID_HOME` to be set and the following SDK components installed:

- `platforms;android-37.0`
- `build-tools;37.0.0` (or higher)

## Signing

Release builds are signed with a keystore generated during setup.
The keystore and its password live in files that are **gitignored**:

```
keystore/statusbarhider.keystore   # PKCS12, RSA 2048, 10 000 days
keystore.properties                 # storeFile, storePassword, keyAlias, keyPassword
```

To generate a new keystore:

```bash
keytool -genkeypair -v \
  -keystore keystore/statusbarhider.keystore \
  -alias statusbarhider -keyalg RSA -keysize 2048 \
  -validity 10000 -storetype PKCS12 \
  -storepass <password> -keypass <password> \
  -dname "CN=StatusBar Hider, OU=Dev, O=Local, L=Local, ST=Local, C=FR"
```

Then create `keystore.properties` at the project root:

```properties
storeFile=keystore/statusbarhider.keystore
storePassword=<password>
keyAlias=statusbarhider
keyPassword=<password>
```

## How it works

1. **Authorize Shizuku** -- grants the app shell-level permissions via the Shizuku binder.
2. **Hide status bar** -- runs shell commands defined in the OEM JSON (e.g. immersive mode + icon blacklist + disable flags).
3. **Check state** -- reads back the current values of those settings.
4. **Restore** -- deletes the settings keys and clears the disable flags.

All commands are OEM-specific. The JSON files in `app/src/main/assets/oem/` define which commands to run. The app auto-detects the OEM from `Build.MANUFACTURER`/`BRAND`/`MODEL`/`DISPLAY` on first launch and saves the pick; the **Config** dropdown under `Status bar` overrides it and **Re-detect OEM** re-runs detection. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to discover commands on a new device.

## App features

- **Custom status bar overlay** -- a Compose bar with clock, date, battery, connectivity, notification and media widgets. Stays visible over the **lock screen** (toggleable) and shows over the system bar while it is hidden.
- **Auto OEM detection** -- matches system properties against available JSON configs on first launch; override or re-detect from the UI.
- **Untested badge** -- configs that were only researched online are labelled `untested` in the header; their troubleshooting notes are printed in the log before the commands run.
- **Hide from launcher** -- disables the launcher activity so the app disappears from the app drawer. Re-access via `mise run show-in-drawer` or Settings > Apps > StatusBar Hider.
- **Boot auto-hide** -- a `BOOT_COMPLETED` receiver re-applies the hide commands on reboot (requires "Start on boot" enabled in Shizuku settings). Disable it with the "Auto-hide after reboot" switch.
- **Export / share logs** -- copies the log to the clipboard, or shares it as a file (the last 500 timestamped lines are kept on disk, including boot-time output).
- **Log panel** -- shows the output of every command for debugging.

## Known limitations

- Disable flags (`cmd statusbar send-disable-flag`) are volatile and reset on reboot / SystemUI restart. The `BOOT_COMPLETED` receiver handles this automatically, but **Shizuku must be configured to start on boot**.
- `ShizukuCmd` calls `IShizukuService.newProcess()` directly via the `aidl` artifact instead of the deprecated `Shizuku.newProcess()` method (private since Shizuku 13.1.5). Stdout and stderr are drained concurrently with a timeout to avoid pipe deadlocks, and the **exit code is honoured** (a failing command is reported as `FAILED`, not silently swallowed).
- The overlay uses `FLAG_SHOW_WHEN_LOCKED` to draw above the keyguard; without the toggle it behaves like a normal overlay window.

## Credits

Inspired by [Essentials](https://github.com/sameerasw/essentials) by Sameera Perera and [SystemUI Tuner](https://github.com/zacharee/Tweaker) by Zachary Wander.

## License

This project is provided as-is for educational purposes. Use at your own risk.
